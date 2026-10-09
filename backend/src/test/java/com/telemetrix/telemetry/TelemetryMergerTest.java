package com.telemetrix.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.Fixtures;
import com.telemetrix.ingestion.openf1.OpenF1CarData;
import com.telemetrix.ingestion.openf1.OpenF1Location;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

// Car data and location are separate OpenF1 feeds at ~3.7 Hz each; merged into one series.
class TelemetryMergerTest {

	static final Instant FROM = Instant.parse("2024-03-02T14:00:00Z");

	static final Instant TO = Instant.parse("2024-03-02T18:00:00Z");

	@Test
	void mergesTheRecordedFeedsOfOneCar() {
		var result = TelemetryMerger.merge(Fixtures.openF1CarData(9472, 1), Fixtures.openF1Locations(9472, 1), FROM, TO);

		assertThat(result.points()).hasSize(806);
		assertThat(result.points()).filteredOn(p -> p.x() != null).hasSize(786); // 20 in the garage, no position
		assertThat(result.dropped()).isZero();
		var first = result.points().stream()
				.filter(p -> p.recordedAt().equals(Instant.parse("2024-03-02T15:18:16.271Z"))).findFirst().orElseThrow();
		assertThat(first.speed()).isEqualTo(286);
		assertThat(first.x()).isEqualTo(-343);
		assertThat(first.y()).isEqualTo(2113);
	}

	@Test
	void recordsGapsInsteadOfFillingThem() {
		var result = TelemetryMerger.merge(Fixtures.openF1CarData(9472, 1), Fixtures.openF1Locations(9472, 1), FROM, TO);

		assertThat(result.gaps()).containsExactly(new TelemetryGap(Instant.parse("2024-03-02T14:03:49.138Z"),
				Instant.parse("2024-03-02T15:18:10.031Z")));
	}

	@Test
	void takesTheNearestLocationWithinHalfASecond() {
		var car = List.of(car("15:00:00.000"), car("15:00:01.000"));
		var locations = List.of(location("14:59:59.900", 1, 1), location("15:00:00.300", 2, 2),
				location("15:00:01.700", 3, 3)); // 0.7 s away from the second sample: too far

		var points = TelemetryMerger.merge(car, locations, FROM, TO).points();

		assertThat(points.get(0).x()).isEqualTo(1);
		assertThat(points.get(1).x()).isNull();
	}

	@Test
	void aPositionOfAllZerosIsNoPosition() {
		var points = TelemetryMerger.merge(List.of(car("15:00:00.000")), List.of(location("15:00:00.100", 0, 0)), FROM,
				TO).points();

		assertThat(points.getFirst().x()).isNull();
		assertThat(points.getFirst().y()).isNull();
	}

	@Test
	void dropsSamplesOutsideTheSessionWindowAndRepeatedTimestamps() {
		var car = List.of(car("15:00:01.000"), car("13:00:00.000"), car("15:00:00.000"), car("15:00:01.000"),
				car("19:00:00.000"));

		var result = TelemetryMerger.merge(car, List.of(), FROM, TO);

		assertThat(result.points()).extracting(TelemetryPoint::recordedAt).containsExactly(at("15:00:00.000"),
				at("15:00:01.000")); // in time order, once each
		assertThat(result.dropped()).isEqualTo(3);
	}

	private static OpenF1CarData car(String time) {
		return new OpenF1CarData(1, odt(time), 250, 100, 0, 7, 11000, 0);
	}

	private static OpenF1Location location(String time, int x, int y) {
		return new OpenF1Location(1, odt(time), x, y, 0);
	}

	private static OffsetDateTime odt(String time) {
		return OffsetDateTime.parse("2024-03-02T" + time + "Z");
	}

	private static Instant at(String time) {
		return Instant.parse("2024-03-02T" + time + "Z");
	}

}
