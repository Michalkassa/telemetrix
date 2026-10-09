package com.telemetrix.event;

import static com.telemetrix.event.TestRace.t;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.telemetrix.ingestion.RaceImported;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

// The Observer pattern: every detected event is published as a Spring application event, once per
// race (until it's imported again), for other modules to listen to.
@SpringJUnitConfig(TimelineServiceTest.Config.class)
@RecordApplicationEvents
class TimelineServiceTest {

	static final RaceEvent SC = new RaceEvent(EventType.SAFETY_CAR, t(250), 3, t(480), 5, Severity.HIGH, List.of(),
			"Safety car", null);

	static final RaceEvent PIT = new RaceEvent(EventType.PIT_STOP, t(100), 2, t(125), 2, Severity.LOW, List.of(1),
			"Pit stop: VER", null);

	@Configuration
	@Import(TimelineService.class)
	static class Config {

		@Bean
		EventDetector safetyCars() {
			return race -> List.of(SC);
		}

		@Bean
		EventDetector pitStops() {
			return race -> List.of(PIT);
		}

	}

	@MockitoBean
	RaceDataLoader loader;

	@Autowired
	TimelineService timelines;

	@Autowired
	ApplicationEvents published;

	@BeforeEach
	void forgetEarlierTests() {
		// the context (and so the service's cache) is shared by these tests
		timelines.onRaceImported(new RaceImported(3, List.of(7L)));
	}

	@Test
	void runsEveryDetectorAndOrdersTheirEventsByTime() {
		given(loader.load(7)).willReturn(new TestRace().car(1, "VER", 0, 0, 1000, 10).build());

		assertThat(timelines.timeline(7).events()).containsExactly(PIT, SC);
	}

	@Test
	void publishesEachDetectedEventOnce() {
		given(loader.load(7)).willReturn(new TestRace().car(1, "VER", 0, 0, 1000, 10).build());

		timelines.timeline(7);
		timelines.timeline(7); // from the cache: nothing new to tell anyone

		assertThat(published.stream(RaceEventDetected.class)).extracting(RaceEventDetected::event)
				.containsExactly(PIT, SC);
		assertThat(published.stream(RaceEventDetected.class)).allSatisfy(e -> assertThat(e.sessionId()).isEqualTo(7));
	}

	@Test
	void aReimportedRaceIsDetectedAgain() {
		given(loader.load(7)).willReturn(new TestRace().car(1, "VER", 0, 0, 1000, 10).build());
		timelines.timeline(7);

		timelines.onRaceImported(new RaceImported(3, List.of(7L)));
		timelines.timeline(7);

		assertThat(published.stream(RaceEventDetected.class)).hasSize(4);
	}

}
