package com.telemetrix.championship;

import static com.telemetrix.event.TestRace.t;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.telemetrix.championship.LiveStandingsService.Context;
import com.telemetrix.championship.LiveStandingsService.Entry;
import com.telemetrix.event.EventType;
import com.telemetrix.event.Severity;
import com.telemetrix.event.TestRace;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

// The championship lead "if the race finished now" changing hands during a race. Laps are 100 s; 2024
// rules (25-18-15..., a point for the fastest lap in the top ten).
class ChampionshipLeadDetectorTest {

	LiveStandingsService live = mock(LiveStandingsService.class);

	Context context(Map<String, Double> before) {
		return new Context(2024, 5, false, Map.of(1, new Entry("max_verstappen", "VER", "Verstappen"), 44,
				new Entry("hamilton", "HAM", "Hamilton")), before, PointsSystem.forSeason(2024));
	}

	@Test
	void theLeaderRetiringHandsTheLeadToTheOther() {
		// VER leads the championship by 15 and runs second, HAM leads the race... until VER stops at 250 s
		given(live.context(7)).willReturn(Optional.of(context(Map.of("max_verstappen", 20.0, "hamilton", 5.0))));
		var race = new TestRace().session(7).laps(10)
				.car(44, "HAM", 0, 0, 1000, 10)
				.car(1, "VER", 0, 0, 250, 2.4) // retires
				.build();

		var events = new ChampionshipLeadDetector(live).detect(race);

		assertThat(events).singleElement().satisfies(e -> {
			assertThat(e.type()).isEqualTo(EventType.CHAMPIONSHIP_LEAD);
			assertThat(e.at()).isEqualTo(t(250));
			assertThat(e.participants()).containsExactly(44, 1);
			assertThat(e.severity()).isEqualTo(Severity.HIGH);
			assertThat(e.summary()).isEqualTo("Championship lead: HAM takes it from VER (if the race finished now)");
		});
	}

	@Test
	void noChangeNoEvent() {
		given(live.context(7)).willReturn(Optional.of(context(Map.of("max_verstappen", 20.0, "hamilton", 5.0))));
		var race = new TestRace().session(7).laps(10)
				.car(1, "VER", 0, 0, 1000, 10)
				.car(44, "HAM", 0, 0, 1010, 10)
				.build();

		assertThat(new ChampionshipLeadDetector(live).detect(race)).isEmpty();
	}

	@Test
	void theFirstRoundHasNoLeadToChange() {
		given(live.context(7)).willReturn(Optional.of(context(Map.of())));
		var race = new TestRace().session(7).laps(10).car(1, "VER", 0, 0, 1000, 10).car(44, "HAM", 0, 0, 1010, 10)
				.build();

		assertThat(new ChampionshipLeadDetector(live).detect(race)).isEmpty();
	}

	@Test
	void withoutTheHistoryNothing() {
		given(live.context(7)).willReturn(Optional.empty());
		var race = new TestRace().session(7).car(1, "VER", 0, 0, 1000, 10).build();

		assertThat(new ChampionshipLeadDetector(live).detect(race)).isEqualTo(List.of());
	}

}
