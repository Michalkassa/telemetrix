package com.telemetrix.championship;

import com.telemetrix.championship.ResultLine.Kind;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Recorded Jolpica data (fixtures/jolpica/championship): each season's race and sprint results with the
// points awarded, and the official final standings.
final class ChampionshipFixtures {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private ChampionshipFixtures() {
	}

	static JsonNode json(String file) {
		try (InputStream in = new ClassPathResource("fixtures/jolpica/championship/" + file).getInputStream()) {
			return JSON.readTree(in);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** A season's results (the API's): a line per car per Grand Prix and sprint. */
	static List<ResultLine> results(int season) {
		List<ResultLine> lines = new ArrayList<>();
		for (JsonNode race : json("results_" + season + ".json").get("races")) {
			int round = race.get("round").asInt();
			race.get("results").forEach(r -> lines.add(line(round, Kind.GRAND_PRIX, r)));
			race.get("sprint").forEach(r -> lines.add(line(round, Kind.SPRINT, r)));
		}
		return lines;
	}

	private static ResultLine line(int round, Kind kind, JsonNode r) {
		String position = r.get("position").asString();
		return new ResultLine(round, kind, r.get("driver").asString(), r.get("constructor").asString(),
				position.chars().allMatch(Character::isDigit) ? Integer.valueOf(position) : null, r.get("points").asDouble());
	}


	static List<Object[]> results1950To1990() {
		List<Object[]> rows = new ArrayList<>();
		try (InputStream in = new ClassPathResource("fixtures/jolpica/championship/results_1950_1990.csv").getInputStream()) {
			String[] lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n");
			for (int i = 1; i < lines.length; i++) {
				String[] f = lines[i].split(",", -1);
				if (f.length < 7) {
					continue;
				}
				rows.add(new Object[] { Integer.parseInt(f[0]), new ResultLine(Integer.parseInt(f[1]),
						f[4].equals("SR") ? Kind.SPRINT : Kind.GRAND_PRIX, f[2], f[3],
						f[5].isEmpty() ? null : Integer.valueOf(f[5]), f[6].isEmpty() ? 0 : Double.parseDouble(f[6])) });
			}
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return rows;
	}

}
