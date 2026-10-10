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
import com.telemetrix.ingestion.ExternalApiException;
import com.telemetrix.ingestion.RateLimitedClient;
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
		client = new OpenF1Client(new RateLimitedClient("OpenF1", builder.build(), PROPS.maxAttempts(),
				PROPS.initialBackoff(), PROPS.minRequestInterval(), time, time));
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
	void fetchesASessionsRaceControlMessages() {
		server.expect(requestTo(BASE + "/race_control?session_key=9165"))
				.andRespond(withSuccess(fixture("race_control_9165.json"), MediaType.APPLICATION_JSON));

		var messages = client.raceControl(9165);

		assertThat(messages).hasSize(76);
		assertThat(messages.get(18)).isEqualTo(new OpenF1RaceControl(OffsetDateTime.parse("2023-09-17T12:36:41Z"), 20,
				"SafetyCar", null, null, null, null, "SAFETY CAR DEPLOYED"));
		assertThat(messages.get(6).sector()).isEqualTo(13);
	}

	@Test
	void fetchesASessionsWeather() {
		server.expect(requestTo(BASE + "/weather?session_key=9165"))
				.andRespond(withSuccess(fixture("weather_9165.json"), MediaType.APPLICATION_JSON));

		var weather = client.weather(9165);

		assertThat(weather).hasSize(176);
		assertThat(weather.getFirst()).isEqualTo(new OpenF1Weather(OffsetDateTime.parse("2023-09-17T11:01:24.028Z"),
				30.0, 38.3, 68.0, 1007.9, 0, 0.8, 106));
	}

	@Test
	void fetchesASessionsTeamRadio() {
		server.expect(requestTo(BASE + "/team_radio?session_key=9165"))
				.andRespond(withSuccess(fixture("team_radio_9165.json"), MediaType.APPLICATION_JSON));

		var radio = client.teamRadio(9165);

		assertThat(radio).hasSize(65);
		assertThat(radio.getFirst().driverNumber()).isEqualTo(24);
		assertThat(radio.getFirst().recordingUrl()).endsWith("/TeamRadio/GUAZHO01_24_20230917_121303.mp3");
	}

	@Test
	void fetchesOneCarsPositionsBetweenTwoMoments() {
		server.expect(requestTo(BASE + "/location?session_key=9472&driver_number=24"
				+ "&date%3E=2024-03-02T15:19:05.235Z&date%3C=2024-03-02T15:19:30.435Z"))
				.andRespond(withSuccess(fixture("location_9472_24_pit.json"), MediaType.APPLICATION_JSON));

		var positions = client.locations(9472, 24, Instant.parse("2024-03-02T15:19:05.235Z"),
				Instant.parse("2024-03-02T15:19:30.435Z"));

		assertThat(positions).hasSize(98);
		assertThat(positions.getFirst().x()).isEqualTo(-67);
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
	void fetchesASessionsLapsWithSectorSplits() {
		server.expect(requestTo(BASE + "/laps?session_key=9472"))
				.andRespond(withSuccess(fixture("laps_9472.json"), MediaType.APPLICATION_JSON));

		var laps = client.laps(9472);

		assertThat(laps).hasSize(1129);
		var lap10 = laps.stream().filter(l -> l.driverNumber() == 1 && l.lapNumber() == 10).findFirst().orElseThrow();
		assertThat(lap10.dateStart()).isEqualTo(OffsetDateTime.parse("2024-03-02T15:18:15.404Z"));
		assertThat(lap10.durationSector1()).isEqualTo(30.986);
		assertThat(lap10.durationSector2()).isEqualTo(42.009);
		assertThat(lap10.durationSector3()).isEqualTo(23.965);
		assertThat(lap10.lapDuration()).isEqualTo(96.96);
		assertThat(lap10.isPitOutLap()).isFalse();
		assertThat(laps).filteredOn(l -> l.durationSector1() == null).hasSize(2);
	}

	@Test
	void fetchesASessionsTyreStints() {
		server.expect(requestTo(BASE + "/stints?session_key=9472"))
				.andRespond(withSuccess(fixture("stints_9472.json"), MediaType.APPLICATION_JSON));

		var stints = client.stints(9472);

		assertThat(stints).hasSize(63);
		assertThat(stints.getFirst()).isEqualTo(new OpenF1Stint(1, 1, "SOFT", 1, 17, 3));
	}

	@Test
	void fetchesASessionsPitStops() {
		server.expect(requestTo(BASE + "/pit?session_key=9472"))
				.andRespond(withSuccess(fixture("pit_9472.json"), MediaType.APPLICATION_JSON));

		var stops = client.pitStops(9472);

		assertThat(stops).hasSize(43);
		var first = stops.getFirst();
		assertThat(first.driverNumber()).isEqualTo(27);
		assertThat(first.lapNumber()).isEqualTo(1);
		assertThat(first.date()).isEqualTo(OffsetDateTime.parse("2024-03-02T15:06:11.115Z"));
		assertThat(first.laneDuration()).isEqualTo(36.6);
		assertThat(first.stopDuration()).isNull();
	}

	@Test
	void fetchesOneCarsTelemetryForASession() {
		server.expect(requestTo(BASE + "/car_data?session_key=9472&driver_number=1"))
				.andRespond(withSuccess(fixture("car_data_9472_1.json"), MediaType.APPLICATION_JSON));

		var samples = client.carData(9472, 1);

		assertThat(samples).hasSize(806);
		assertThat(samples.getLast()).satisfies(s -> {
			assertThat(s.driverNumber()).isEqualTo(1);
			assertThat(s.date()).isNotNull();
			assertThat(s.speed()).isNotNull();
			assertThat(s.gear()).isNotNull();
		});
	}

	@Test
	void fetchesOneCarsLocationsForASession() {
		server.expect(requestTo(BASE + "/location?session_key=9472&driver_number=1"))
				.andRespond(withSuccess(fixture("location_9472_1.json"), MediaType.APPLICATION_JSON));

		var samples = client.locations(9472, 1);

		assertThat(samples).hasSize(819);
		assertThat(samples.getFirst()).isEqualTo(
				new OpenF1Location(1, OffsetDateTime.parse("2024-03-02T14:03:43.899Z"), 0, 0, 0));
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

		assertThatThrownBy(() -> client.meetings(2024)).isInstanceOf(ExternalApiException.class)
				.hasMessageContaining("OpenF1 GET /meetings?year=2024")
				.hasMessageContaining("3 attempts");
		server.verify();
		assertThat(time.sleeps()).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
	}

	@Test
	void doesNotRetryARejectedRequest() {
		server.expect(times(1), requestTo(BASE + "/meetings?year=2024")).andRespond(withBadRequest());

		assertThatThrownBy(() -> client.meetings(2024)).isInstanceOf(ExternalApiException.class)
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

		assertThatThrownBy(() -> client.meetings(2024)).isInstanceOf(ExternalApiException.class)
				.hasMessageContaining("401");
	}

	private static ClassPathResource fixture(String name) {
		return new ClassPathResource("fixtures/openf1/" + name);
	}

}
