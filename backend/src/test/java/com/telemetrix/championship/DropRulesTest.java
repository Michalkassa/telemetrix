package com.telemetrix.championship;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;

// Until 1990 only a driver's best results counted (some seasons: the best of each half). For every season
// 1950-1990, each driver's total from the imported history's results equals the official one (recorded
// from the Jolpica API).
class DropRulesTest {

	static final Map<Integer, List<ResultLine>> RESULTS = ChampionshipFixtures.results1950To1990().stream()
			.collect(Collectors.groupingBy(r -> (Integer) r[0], Collectors.mapping(r -> (ResultLine) r[1], Collectors.toList())));

	// Jolpica's own data disagrees here: its results give Trintignant 11.33 and Farina 10.33 (shared
	// drives, best 5) where its standings say 10 and 9.
	static final Map<Integer, List<String>> SOURCE_DISAGREES = Map.of(1955, List.of("trintignant", "farina"));

	static Stream<Integer> seasons() {
		return IntStream.rangeClosed(1950, 1990).boxed();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("seasons")
	void everyDriversTotalEqualsTheOfficialOne(int season) {
		var standings = StandingsCalculator.compute(season, RESULTS.get(season));
		Map<String, Double> mine = standings.drivers().stream().collect(Collectors.toMap(Standing::id, Standing::points));

		for (JsonNode o : ChampionshipFixtures.json("final-standings/drivers_" + season + ".json")) {
			String driver = o.get("driver").asString();
			if (SOURCE_DISAGREES.getOrDefault(season, List.of()).contains(driver)) {
				continue;
			}
			assertThat(mine.get(driver)).as("%d %s", season, driver).isCloseTo(o.get("points").asDouble(), within(0.01));
		}
	}

}
