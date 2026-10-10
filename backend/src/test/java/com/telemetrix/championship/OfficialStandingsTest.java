package com.telemetrix.championship;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;

// Outer test (the Check-it): final standings for 1950, 2008, 2021 and 2024, rebuilt race by race from
// the results with the points awarded, equal the official tables — all recorded from the Jolpica API.
// Cars level on points and on every count-back place are tied: the official table lists them in some
// order, so they're compared as a group. Drivers without points aren't ranked ("-") at all.
class OfficialStandingsTest {

	@ParameterizedTest(name = "{0} drivers")
	@ValueSource(ints = { 1950, 2008, 2021, 2024 })
	void driversStandingsEqualTheOfficialTable(int season) {
		var standings = StandingsCalculator.compute(season, ChampionshipFixtures.results(season));
		JsonNode official = ChampionshipFixtures.json("standings_" + season + ".json").get("drivers");

		assertThat(groups(standings.drivers())).isEqualTo(officialGroups(official, "driver", standings.drivers()));
		for (JsonNode o : official) {
			var mine = find(standings.drivers(), o.get("driver").asString());
			assertThat(mine.points()).as("points of %s", mine.id()).isEqualTo(o.get("points").asDouble());
			assertThat(mine.wins()).as("wins of %s", mine.id()).isEqualTo(o.get("wins").asInt());
		}
	}

	@ParameterizedTest(name = "{0} constructors")
	@ValueSource(ints = { 2008, 2021, 2024 })
	void constructorsStandingsEqualTheOfficialTable(int season) {
		var standings = StandingsCalculator.compute(season, ChampionshipFixtures.results(season));
		JsonNode official = ChampionshipFixtures.json("standings_" + season + ".json").get("constructors");

		assertThat(standings.constructors()).extracting(Standing::id)
				.containsExactlyElementsOf(stream(official).map(o -> o.get("constructor").asString()).toList());
		for (JsonNode o : official) {
			assertThat(find(standings.constructors(), o.get("constructor").asString()).points())
					.isEqualTo(o.get("points").asDouble());
		}
	}

	// the ranked part of a table (points > 0) as groups of tied entries, in order
	private static List<Set<String>> groups(List<Standing> table) {
		Map<String, Set<String>> byKey = new LinkedHashMap<>();
		table.stream().filter(s -> s.points() > 0)
				.forEach(s -> byKey.computeIfAbsent(s.tieKey(), k -> new TreeSet<>()).add(s.id()));
		return new ArrayList<>(byKey.values());
	}

	// the official table grouped the same way: by points and the count-back we computed for each driver
	private static List<Set<String>> officialGroups(JsonNode official, String field, List<Standing> mine) {
		Map<String, Standing> byId = mine.stream().collect(Collectors.toMap(Standing::id, Function.identity()));
		Map<String, Set<String>> byKey = new LinkedHashMap<>();
		stream(official).filter(o -> o.get("points").asDouble() > 0)
				.forEach(o -> byKey.computeIfAbsent(byId.get(o.get(field).asString()).tieKey(), k -> new TreeSet<>())
						.add(o.get(field).asString()));
		return new ArrayList<>(byKey.values());
	}

	private static Standing find(List<Standing> table, String id) {
		return table.stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow(() -> new AssertionError(id));
	}

	private static java.util.stream.Stream<JsonNode> stream(JsonNode array) {
		List<JsonNode> list = new ArrayList<>();
		array.forEach(list::add);
		return list.stream();
	}

}
