package com.telemetrix.result;

import com.telemetrix.common.NotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

// A race's results and lap times for the race hub, read with SQL: the classification from the Jolpica
// history (1950 on); lap times from OpenF1 when the race has been opened (2023 on, every lap), else from
// the history (1996 on).
@Service
public class RaceResultsService {

	private final JdbcTemplate jdbc;

	RaceResultsService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public RaceResults results(long raceId) {
		requireRace(raceId);
		var rows = jdbc.query("""
				select sr.position, d.code, d.given_name, d.family_name, c.name as team, e.car_number, sr.grid,
				       sr.laps_completed, sr.time_ms, sr.status, sr.status_detail, sr.classified, sr.points
				from session_results sr
				join race_entries e on e.id = sr.race_entry_id
				join drivers d on d.id = e.driver_id
				join constructors c on c.id = e.constructor_id
				where e.race_id = ? and sr.session_type = 'R'
				order by sr.position nulls last, e.car_number""", (rs, i) -> new RaceResults.Row(
				(Integer) rs.getObject("position", Integer.class), trim(rs.getString("code")), rs.getString("given_name"),
				rs.getString("family_name"), rs.getString("team"), rs.getObject("car_number", Integer.class),
				rs.getObject("grid", Integer.class), rs.getObject("laps_completed", Integer.class),
				rs.getObject("time_ms", Long.class), rs.getString("status"), rs.getString("status_detail"),
				rs.getObject("classified", Boolean.class),
				rs.getBigDecimal("points") == null ? null : rs.getBigDecimal("points").doubleValue()), raceId);
		return new RaceResults(raceId, rows);
	}

	public LapTimes lapTimes(long raceId) {
		requireRace(raceId);
		Map<Integer, Integer> positionByNumber = new LinkedHashMap<>();
		Map<Integer, String> surnameByNumber = new LinkedHashMap<>();
		Map<Integer, String> codeByNumber = new LinkedHashMap<>();
		for (RaceResults.Row r : results(raceId).results()) {
			if (r.number() != null) {
				positionByNumber.putIfAbsent(r.number(), r.position());
				surnameByNumber.putIfAbsent(r.number(), r.surname());
				codeByNumber.putIfAbsent(r.number(), r.code());
			}
		}

		// OpenF1: the race session's laps, once the race has been opened
		Map<Integer, List<LapTimes.Lap>> openF1 = new LinkedHashMap<>();
		jdbc.query("""
				select l.driver_number, l.lap_number, l.lap_ms from laps l
				join sessions s on s.id = l.session_id
				where s.race_id = ? and s.name = 'Race' and l.lap_ms is not null
				order by l.driver_number, l.lap_number""", rs -> {
			openF1.computeIfAbsent(rs.getInt("driver_number"), n -> new ArrayList<>())
					.add(new LapTimes.Lap(rs.getInt("lap_number"), rs.getLong("lap_ms")));
		}, raceId);
		if (!openF1.isEmpty()) {
			Map<Integer, String> openF1Codes = new LinkedHashMap<>();
			jdbc.query("""
					select sd.driver_number, sd.code from session_drivers sd
					join sessions s on s.id = sd.session_id
					where s.race_id = ? and s.name = 'Race'""",
					rs -> {
						openF1Codes.put(rs.getInt("driver_number"), rs.getString("code"));
					}, raceId);
			var cars = openF1.entrySet().stream()
					.map(e -> new LapTimes.Car(e.getKey(), openF1Codes.getOrDefault(e.getKey(), codeByNumber.get(e.getKey())),
							surnameByNumber.get(e.getKey()), positionByNumber.get(e.getKey()), e.getValue()))
					.sorted(FINISHING_ORDER)
					.toList();
			return new LapTimes(raceId, LapTimes.Source.OPENF1, cars);
		}

		// the history: Jolpica lap times (1996 on)
		Map<Integer, LapTimes.Car> history = new LinkedHashMap<>();
		jdbc.query("""
				select e.car_number, d.code, d.family_name, lt.lap, lt.time_ms from lap_times lt
				join race_entries e on e.id = lt.race_entry_id
				join drivers d on d.id = e.driver_id
				where e.race_id = ? and lt.session_type = 'R' and lt.time_ms is not null and e.car_number is not null
				order by e.car_number, lt.lap""", rs -> {
			int number = rs.getInt("car_number");
			String code = trim(rs.getString("code"));
			String surname = rs.getString("family_name");
			history.computeIfAbsent(number, n -> new LapTimes.Car(n, code, surname, positionByNumber.get(n),
					new ArrayList<>()))
					.laps().add(new LapTimes.Lap(rs.getInt("lap"), rs.getLong("time_ms")));
		}, raceId);
		if (!history.isEmpty()) {
			return new LapTimes(raceId, LapTimes.Source.JOLPICA,
					history.values().stream().sorted(FINISHING_ORDER).toList());
		}
		return new LapTimes(raceId, LapTimes.Source.NONE, List.of());
	}

	private static final Comparator<LapTimes.Car> FINISHING_ORDER = Comparator
			.comparing(LapTimes.Car::position, Comparator.nullsLast(Comparator.naturalOrder()))
			.thenComparingInt(LapTimes.Car::number);

	private void requireRace(long raceId) {
		if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from races where id = ?)", Boolean.class,
				raceId))) {
			throw new NotFoundException("Race", raceId);
		}
	}

	// drivers.code is char(3): blank-padded or null
	private static String trim(String code) {
		return code == null || code.isBlank() ? null : code.trim();
	}

}
