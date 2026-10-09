package com.telemetrix.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// The cases in contracts/progress-timeline.json, which the frontend runs too: the browser's replay
// and the server's frames read progress exactly the same way.
class ProgressTimelineContractTest {

	static Stream<Arguments> cases() {
		JsonNode root = JsonMapper.builder().build().readTree(Path.of("../contracts/progress-timeline.json").toFile());
		List<Arguments> cases = new ArrayList<>();
		root.get("cases").forEach(c -> cases.add(Arguments.of(c.get("name").asString(), c)));
		return cases.stream();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("cases")
	void readsProgressLikeTheFrontend(String name, JsonNode c) {
		List<ProgressTimeline.TimingPoint> points = new ArrayList<>();
		c.get("points").forEach(p -> points.add(
				new ProgressTimeline.TimingPoint(Instant.ofEpochMilli(p.get(0).asLong()), p.get(1).asDouble())));
		var timeline = ProgressTimeline.of(points);

		c.get("progressAt").forEach(q -> assertThat(timeline.lapsDoneAt(Instant.ofEpochMilli(q.get(0).asLong())))
				.as("progressAt(%d)", q.get(0).asLong())
				.isCloseTo(q.get(1).asDouble(), within(1e-9)));
		c.get("reachedAt").forEach(q -> {
			Instant reached = timeline.reachedAt(q.get(0).asDouble());
			if (q.get(1).isNull()) {
				assertThat(reached).as("reachedAt(%s)", q.get(0)).isNull();
			}
			else {
				assertThat(reached).as("reachedAt(%s)", q.get(0)).isEqualTo(Instant.ofEpochMilli(q.get(1).asLong()));
			}
		});
	}

}
