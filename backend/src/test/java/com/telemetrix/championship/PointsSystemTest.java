package com.telemetrix.championship;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.championship.PointsSystem.Scale;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;

// The points rules of each era (the Strategy pattern), checked against what was actually awarded: every
// result of the recorded seasons (Jolpica API) gets exactly its awarded points from its season's rules.
class PointsSystemTest {

	// races that didn't pay full points (found in the data: the winner's points)
	static final Map<String, Scale> SCALE = Map.of("1975/4", Scale.HALF, "1975/12", Scale.HALF, "2021/12", Scale.HALF,
			"2014/19", Scale.DOUBLE);

	static Stream<Arguments> awardedPoints() {
		List<Arguments> cases = new ArrayList<>();
		for (int season : new int[] { 1960, 1975, 1995, 2008, 2014, 2015, 2021, 2024 }) {
			for (JsonNode race : ChampionshipFixtures.json("results_" + season + ".json").get("races")) {
				int round = race.get("round").asInt();
				Scale scale = SCALE.getOrDefault(season + "/" + round, Scale.FULL);
				Map<String, Integer> seen = new HashMap<>();
				race.get("results").forEach(r -> seen.merge(r.get("position").asString(), 1, Integer::sum));
				for (JsonNode r : race.get("results")) {
					String position = r.get("position").asString();
					if (seen.get(position) > 1) {
						continue; // a shared drive: not something a live race has
					}
					cases.add(Arguments.of(season, round, "GP", position, r.get("fastestLapRank").asInt(0) == 1, scale,
							r.get("points").asDouble(), r.get("driver").asString()));
				}
				for (JsonNode r : race.get("sprint")) {
					cases.add(Arguments.of(season, round, "SPRINT", r.get("position").asString(), false, Scale.FULL,
							r.get("points").asDouble(), r.get("driver").asString()));
				}
			}
		}
		return cases.stream();
	}

	@ParameterizedTest(name = "{0} round {1} {2} {3}: {7}")
	@MethodSource("awardedPoints")
	void everyResultGetsTheAwardedPoints(int season, int round, String kind, String position, boolean fastestLap,
			Scale scale, double awarded, String driver) {
		PointsSystem rules = PointsSystem.forSeason(season);
		Integer place = position.chars().allMatch(Character::isDigit) ? Integer.valueOf(position) : null;

		double points = kind.equals("SPRINT") ? rules.sprint(place) : rules.race(place, fastestLap, scale);

		assertThat(points).isEqualTo(awarded);
	}

	@Test
	void theFiftiesPaidFiveAndAPointForTheFastestLapEvenToARetiredCar() {
		var fifties = PointsSystem.forSeason(1950);
		assertThat(List.of(1, 2, 3, 4, 5, 6).stream().map(p -> fifties.race(p, false, Scale.FULL)))
				.containsExactly(8.0, 6.0, 4.0, 3.0, 2.0, 0.0);
		assertThat(fifties.race(1, true, Scale.FULL)).isEqualTo(9.0);
		assertThat(fifties.race(null, true, Scale.FULL)).isEqualTo(1.0); // as the data shows for 1950
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(ints = { 1950, 1959, 1960, 1961, 1990, 1991, 2002, 2003, 2009, 2010, 2018, 2019, 2024, 2025 })
	void theEraBoundariesFoundInTheData(int season) {
		var rules = PointsSystem.forSeason(season);
		double winner = rules.race(1, false, Scale.FULL);
		double expected = season <= 1959 ? 8 : season == 1960 ? 8 : season <= 1990 ? 9 : season <= 2002 ? 10
				: season <= 2009 ? 10 : 25;
		assertThat(winner).isEqualTo(expected);
		boolean fastestLapPoint = rules.race(10, true, Scale.FULL) > rules.race(10, false, Scale.FULL);
		assertThat(fastestLapPoint).isEqualTo(season <= 1959 || (season >= 2019 && season <= 2024));
	}

	@Test
	void sprintsFrom2021() {
		assertThat(PointsSystem.forSeason(2020).sprint(1)).isZero();
		assertThat(PointsSystem.forSeason(2021).sprint(3)).isEqualTo(1.0);
		assertThat(PointsSystem.forSeason(2022).sprint(8)).isEqualTo(1.0);
	}

}
