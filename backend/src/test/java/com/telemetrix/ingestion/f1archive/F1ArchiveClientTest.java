package com.telemetrix.ingestion.f1archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.telemetrix.FakeTime;
import com.telemetrix.ingestion.RateLimitedClient;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// The F1 live-timing archive against a mock server replaying recorded files. The year's Index.json
// says where each session's files are (its session keys are OpenF1's).
class F1ArchiveClientTest {

	static final String BASE = "https://archive.test/static";

	static final String SINGAPORE_RACE = BASE + "/2023/2023-09-17_Singapore_Grand_Prix/2023-09-17_Race";

	MockRestServiceServer server;

	F1ArchiveClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
		server = MockRestServiceServer.bindTo(builder).build();
		var time = new FakeTime(Instant.parse("2026-01-01T00:00:00Z"));
		client = new F1ArchiveClient(new RateLimitedClient("F1 archive", builder.build(), 3, Duration.ofSeconds(1),
				Duration.ofSeconds(1), time, time));
	}

	@Test
	void findsTheSessionInTheYearsIndexAndFetchesItsStreams() {
		server.expect(once(), requestTo(BASE + "/2023/Index.json")).andRespond(json("index_2023.json"));
		server.expect(requestTo(SINGAPORE_RACE + "/TrackStatus.jsonStream"))
				.andRespond(stream("singapore_2023_race_TrackStatus.jsonStream"));
		server.expect(requestTo(SINGAPORE_RACE + "/SessionData.jsonStream"))
				.andRespond(stream("singapore_2023_race_SessionData.jsonStream"));

		var streams = client.trackStatus(2023, 9165).orElseThrow();

		server.verify();
		assertThat(streams.trackStatus()).contains("{\"Status\":\"4\",\"Message\":\"SCDeployed\"}");
		assertThat(streams.sessionData()).contains("\"SessionStatus\":\"Started\"");
	}

	@Test
	void fetchesEachYearsIndexOnlyOnce() {
		server.expect(once(), requestTo(BASE + "/2023/Index.json")).andRespond(json("index_2023.json"));

		assertThat(client.trackStatus(2023, 1)).isEmpty();
		assertThat(client.trackStatus(2023, 2)).isEmpty();

		server.verify();
	}

	@Test
	void aSessionNotInTheIndexIsEmpty() {
		server.expect(requestTo(BASE + "/2023/Index.json")).andRespond(json("index_2023.json"));

		assertThat(client.trackStatus(2023, 123456)).isEmpty();
	}

	@Test
	void aMissingFileIsEmpty() {
		server.expect(requestTo(BASE + "/2023/Index.json")).andRespond(json("index_2023.json"));
		server.expect(requestTo(SINGAPORE_RACE + "/TrackStatus.jsonStream")).andRespond(withResourceNotFound());

		assertThat(client.trackStatus(2023, 9165)).isEmpty();
	}

	@Test
	void aYearWithoutAnIndexIsEmpty() {
		server.expect(requestTo(BASE + "/2018/Index.json")).andRespond(withResourceNotFound());

		assertThat(client.trackStatus(2018, 1)).isEmpty();
	}

	static org.springframework.test.web.client.ResponseCreator json(String name) {
		return withSuccess(new ClassPathResource("fixtures/f1archive/" + name), MediaType.APPLICATION_JSON);
	}

	static org.springframework.test.web.client.ResponseCreator stream(String name) {
		return withSuccess(new ClassPathResource("fixtures/f1archive/" + name), MediaType.APPLICATION_OCTET_STREAM);
	}

}
