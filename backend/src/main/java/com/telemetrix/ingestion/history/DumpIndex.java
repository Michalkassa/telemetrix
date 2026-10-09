package com.telemetrix.ingestion.history;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

// The dump's small tables in memory, so the big files (results, laps, pit stops) can be streamed and
// each row resolved to natural keys: Jolpica's internal ids -> season year, round number, driver
// and constructor refs, session type. Rebuilt at the start of every run (and every restart).
@Component
class DumpIndex {

	record Round(int year, Integer number, boolean cancelled, String circuitRef) {

		boolean importable() {
			return !cancelled && number != null;
		}

	}

	record Entry(Round round, String driverRef, String teamRef, Integer carNumber) {
	}

	record Session(String roundId, String type, int number) {
	}

	record SessionEntry(String sessionId, String roundEntryId) {
	}

	private Map<String, Integer> seasonYears = Map.of();

	private Map<String, String> circuitRefs = Map.of();

	private Map<String, Round> rounds = Map.of();

	private Map<String, Entry> entries = Map.of();

	private Map<String, Session> sessions = Map.of();

	private Map<String, SessionEntry> sessionEntries = Map.of();

	private Map<String, Integer> pitLapNumbers = Map.of();

	private Map<String, Long> ourEntryIds = Map.of();

	synchronized void load(Path dir) {
		seasonYears = new HashMap<>();
		for (var row : DumpCsv.readAll(dir.resolve("formula_one_season.csv"))) {
			seasonYears.put(row.get("id"), Integer.valueOf(row.get("year")));
		}
		circuitRefs = new HashMap<>();
		for (var row : DumpCsv.readAll(dir.resolve("formula_one_circuit.csv"))) {
			circuitRefs.put(row.get("id"), row.get("reference"));
		}
		Map<String, String> driverRefs = new HashMap<>();
		for (var row : DumpCsv.readAll(dir.resolve("formula_one_driver.csv"))) {
			driverRefs.put(row.get("id"), row.get("reference"));
		}
		Map<String, String> teamRefs = new HashMap<>();
		for (var row : DumpCsv.readAll(dir.resolve("formula_one_team.csv"))) {
			teamRefs.put(row.get("id"), row.get("reference"));
		}
		Map<String, String[]> teamDrivers = new HashMap<>();
		for (var row : DumpCsv.readAll(dir.resolve("formula_one_teamdriver.csv"))) {
			teamDrivers.put(row.get("id"), new String[] { driverRefs.get(row.get("driver_id")), teamRefs.get(row.get("team_id")) });
		}
		rounds = new HashMap<>();
		for (var row : DumpCsv.readAll(dir.resolve("formula_one_round.csv"))) {
			rounds.put(row.get("id"), new Round(seasonYears.get(row.get("season_id")), JolpicaValues.integer(row.get("number")),
					"t".equals(row.get("is_cancelled")), circuitRefs.get(row.get("circuit_id"))));
		}
		entries = new HashMap<>();
		for (var row : DumpCsv.readAll(dir.resolve("formula_one_roundentry.csv"))) {
			String[] teamDriver = teamDrivers.get(row.get("team_driver_id"));
			Round round = rounds.get(row.get("round_id"));
			if (teamDriver != null && round != null) {
				entries.put(row.get("id"), new Entry(round, teamDriver[0], teamDriver[1], JolpicaValues.integer(row.get("car_number"))));
			}
		}
		sessions = new HashMap<>();
		for (var row : DumpCsv.readAll(dir.resolve("formula_one_session.csv"))) {
			// a session without a number is the only one of its type that weekend
			Integer number = JolpicaValues.integer(row.get("number"));
			sessions.put(row.get("id"), new Session(row.get("round_id"), row.get("type"), number == null ? 1 : number));
		}
		sessionEntries = new HashMap<>();
		for (var row : DumpCsv.readAll(dir.resolve("formula_one_sessionentry.csv"))) {
			sessionEntries.put(row.get("id"), new SessionEntry(row.get("session_id"), row.get("round_entry_id")));
		}
		pitLapNumbers = pitLapNumbers(dir);
		ourEntryIds = Map.of();
	}

	// Pit stops point at a lap row; only those laps' numbers are kept (the lap file is large).
	private static Map<String, Integer> pitLapNumbers(Path dir) {
		Set<String> lapIds = new HashSet<>();
		for (var row : DumpCsv.readAll(dir.resolve("formula_one_pitstop.csv"))) {
			lapIds.add(row.get("lap_id"));
		}
		Map<String, Integer> numbers = new HashMap<>();
		var reader = DumpCsv.reader(dir.resolve("formula_one_lap.csv"), "pitLaps");
		reader.open(new org.springframework.batch.infrastructure.item.ExecutionContext());
		try {
			Map<String, String> row;
			while ((row = reader.read()) != null) {
				if (lapIds.contains(row.get("id"))) {
					numbers.put(row.get("id"), JolpicaValues.integer(row.get("number")));
				}
			}
		}
		catch (Exception e) {
			throw new IllegalStateException("Could not read lap numbers for pit stops", e);
		}
		finally {
			reader.close();
		}
		return numbers;
	}

	/** Our race_entries ids, keyed by entryKey(...): filled once the entries are stored. */
	synchronized void linkEntries(Map<String, Long> ourIdsByKey) {
		Map<String, Long> ids = new HashMap<>();
		entries.forEach((jolpicaId, entry) -> {
			Long ours = ourIdsByKey.get(entryKey(entry.round().year(), entry.round().number(), entry.driverRef(),
					entry.teamRef(), entry.carNumber()));
			if (ours != null) {
				ids.put(jolpicaId, ours);
			}
		});
		ourEntryIds = ids;
	}

	static String entryKey(int year, Integer round, String driverRef, String teamRef, Integer carNumber) {
		return year + "|" + round + "|" + driverRef + "|" + teamRef + "|" + carNumber;
	}

	Integer seasonYear(String seasonId) {
		return seasonYears.get(seasonId);
	}

	String circuitRef(String circuitId) {
		return circuitRefs.get(circuitId);
	}

	Round round(String roundId) {
		return rounds.get(roundId);
	}

	Entry entry(String roundEntryId) {
		return entries.get(roundEntryId);
	}

	Session session(String sessionId) {
		return sessions.get(sessionId);
	}

	SessionEntry sessionEntry(String sessionEntryId) {
		return sessionEntries.get(sessionEntryId);
	}

	Integer pitLapNumber(String lapId) {
		return pitLapNumbers.get(lapId);
	}

	Long ourEntryId(String roundEntryId) {
		return ourEntryIds.get(roundEntryId);
	}

}
