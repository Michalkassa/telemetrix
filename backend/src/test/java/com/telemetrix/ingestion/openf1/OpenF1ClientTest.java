package com.telemetrix.ingestion.openf1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;

import com.telemetrix.FakeTime;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// The OpenF1 client against a mock server replaying recorded OpenF1 responses. No network, no
// real waiting: FakeTime records every sleep instead of sleeping.
class OpenF1ClientTest {

	static final String BASE = "https://openf1.test/v1";

	static final OpenF1Properties PROPS = new OpenF1Properties(BASE, Duration.ofSeconds(10), 3,
			Duration.ofSeconds(1), Duration.ofMillis(500));

	MockRestServiceServer server;

	FakeTime time;

	OpenF1Client client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
		server = MockRestServiceServer.bindTo(builder).build();
		time = new FakeTime(Instant.parse("2026-01-01T00:00:00Z"));
		client = new OpenF1Client(builder.build(), PROPS, time, time);
	}

	@Test
	void fetchesAYearsMeetings() {
		server.expect(requestTo(BASE + "/meetings?year=2024")).andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(fixture("meetings_2024.json"), MediaType.APPLICATION_JSON));

		var meetings = client.meetings(2024);

		server.verify();
		assertThat(meetings).hasSize(25);
		assertThat(meetings.get(1)).isEqualTo(new OpenF1Meeting(1229, "Bahrain Grand Prix",
				"FORMULA 1 GULF AIR BAHRAIN GRAND PRIX 2024", "Sakhir", "BRN", "Bahrain", 63, "Sakhir",
				OffsetDateTime.parse("2024-02-29T11:30:00Z"), OffsetDateTime.parse("2024-03-02T17:00:00Z"), 2024,
				false));
	}

	@Test
	void fetchesAMeetingsSessions() {
		server.expect(requestTo(BASE + "/sessions?meeting_key=1229"))
				.andRespond(withSuccess(fixture("sessions_1229.json"), MediaType.APPLICATION_JSON));

		var sessions = client.sessions(1229);

		assertThat(sessions).extracting(OpenF1Session::sessionName)
				.containsExactly("Practice 1", "Practice 2", "Practice 3", "Qualifying", "Race");
		assertThat(sessions.getLast()).isEqualTo(new OpenF1Session(9472, 1229, "Race", "Race", 63,
				OffsetDateTime.parse("2024-03-02T15:00:00Z"), OffsetDateTime.parse("2024-03-02T17:00:00Z"), 2024,
				false));
	}

	@Test
	void fetchesASessionsDriversKeepingMissingFieldsAsNull() {
		server.expect(requestTo(BASE + "/drivers?session_key=9158"))
				.andRespond(withSuccess(fixture("drivers_9158.json"), MediaType.APPLICATION_JSON));

		var drivers = client.drivers(9158);

		assertThat(drivers).hasSize(20);
		var lawson = drivers.stream().filter(d -> d.driverNumber() == 40).findFirst().orElseThrow();
		assertThat(lawson.nameAcronym()).isEqualTo("LAW");
		assertThat(lawson.firstName()).isEqualTo("Liam");
		assertThat(lawson.lastName()).isEqualTo("Lawson");
		assertThat(lawson.teamName()).isEqualTo("AlphaTauri");
		assertThat(lawson.countryCode()).isEqualTo("NZL");
		assertThat(lawson.headshotUrl()).isNull();
	}

	@Test
	void noResultsIsAnEmptyListNotAnError() {
		// OpenF1 answers "nothing matches" with a 404
		server.expect(requestTo(BASE + "/meetings?year=1990"))
				.andRespond(withResourceNotFound().contentType(MediaType.APPLICATION_JSON)
						.body("{\"detail\":\"No results found.\"}"));

		assertThat(client.meetings(1990)).isEmpty();
	}

	@Test
	void retriesAfterTooManyRequestsAndSucceeds() {
		server.expect(times(1), requestTo(BASE + "/sessions?meeting_key=1229")).andRespond(withTooManyRequests());
		server.expect(times(1), requestTo(BASE + "/sessions?meeting_key=1229"))
				.andRespond(withSuccess(fixture("sessions_1229.json"), MediaType.APPLICATION_JSON));

		assertThat(client.sessions(1229)).hasSize(5);
		server.verify();
		assertThat(time.sleeps()).containsExactly(Duration.ofSeconds(1));
	}

	@Test
	void waitsAsLongAsRetryAfterAsks() {
		server.expect(times(1), requestTo(BASE + "/sessions?meeting_key=1229"))
				.andRespond(withTooManyRequests().header(HttpHeaders.RETRY_AFTER, "7"));
		server.expect(times(1), requestTo(BASE + "/sessions?meeting_key=1229"))
				.andRespond(withSuccess(fixture("sessions_1229.json"), MediaType.APPLICATION_JSON));

		client.sessions(1229);

		assertThat(time.sleeps()).containsExactly(Duration.ofSeconds(7));
	}

	@Test
	void backsOffExponentiallyOnServerErrorsThenGivesUp() {
		server.expect(times(3), requestTo(BASE + "/meetings?year=2024")).andRespond(withServerError());

		assertThatThrownBy(() -> client.meetings(2024)).isInstanceOf(OpenF1Exception.class)
				.hasMessageContaining("/meetings?year=2024")
				.hasMessageContaining("3 attempts");
		server.verify();
		assertThat(time.sleeps()).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
	}

	@Test
	void doesNotRetryARejectedRequest() {
		server.expect(times(1), requestTo(BASE + "/meetings?year=2024")).andRespond(withBadRequest());

		assertThatThrownBy(() -> client.meetings(2024)).isInstanceOf(OpenF1Exception.class)
				.hasMessageContaining("400");
		server.verify();
		assertThat(time.sleeps()).isEmpty();
	}

	@Test
	void spacesRequestsOutToStayUnderTheRateLimit() {
		server.expect(times(3), requestTo(BASE + "/sessions?meeting_key=1229"))
				.andRespond(withSuccess(fixture("sessions_1229.json"), MediaType.APPLICATION_JSON));

		client.sessions(1229);
		client.sessions(1229);
		time.advance(Duration.ofSeconds(5)); // a pause longer than the interval: no wait needed
		client.sessions(1229);

		assertThat(time.sleeps()).containsExactly(Duration.ofMillis(500));
	}

	@Test
	void treatsAnUnexpectedStatusAsAnError() {
		server.expect(requestTo(BASE + "/meetings?year=2024")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

		assertThatThrownBy(() -> client.meetings(2024)).isInstanceOf(OpenF1Exception.class)
				.hasMessageContaining("401");
	}

	private static ClassPathResource fixture(String name) {
		return new ClassPathResource("fixtures/openf1/" + name);
	}

}
