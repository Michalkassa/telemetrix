package com.telemetrix.compare;

import static com.telemetrix.analytics.DriverStatsService.RED_FLAG_MS;

import com.telemetrix.common.BadRequestException;
import com.telemetrix.common.NotFoundException;
import com.telemetrix.compare.CircuitComparison.LapPace;
import com.telemetrix.compare.CircuitComparison.Stint;
import com.telemetrix.compare.CircuitComparison.Strategy;
import com.telemetrix.compare.DriverComparison.Result;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Comparisons across races, in SQL: one circuit's Grand Prix in several years (pace lap by lap, strategies),
// and two drivers through a season. Lap times are the history's (1996 on); compounds come from OpenF1's
// stints, which a race has once it was opened in the app; otherwise stints run between the pit stops.
@Service
@Transactional(readOnly = true)
public class CompareService {

	private final JdbcTemplate jdbc;

	CompareService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Every year the circuit held a Grand Prix. */
	public List<Integer> circuitSeasons(String ref) {
		Long id = jdbc.query("select id from circuits where ref = ?", (rs, i) -> rs.getLong("id"), ref).stream()
			.findFirst()
			.orElseThrow(() -> new NotFoundException("Circuit", ref));
		return jdbc.queryForList("select distinct season_year::int from races where circuit_id = ? order by 1",
				Integer.class, id);
	}

	public CircuitComparison circuit(String ref, List<Integer> years) {
		List<Integer> distinct = years.stream().distinct().sorted().toList();
		if (distinct.size() < 2 || distinct.size() > 4) {
			throw new BadRequestException("Compare two to four different years");
		}
		var circuit = jdbc.query("select id, ref, name from circuits where ref = ?",
				(rs, i) -> new Object[] { rs.getLong("id"), rs.getString("ref"), rs.getString("name") }, ref)
			.stream()
			.findFirst()
			.orElseThrow(() -> new NotFoundException("Circuit", ref));
		var seasons = jdbc.queryForList("select distinct season_year::int from races where circuit_id = ? order by 1",
				Integer.class, circuit[0]);
		Map<Integer, Object[]> races = new HashMap<>();
		jdbc.query("""
				select id, season_year, name, race_date from races
				where circuit_id = ? and season_year = any(?)""", rs -> {
			races.put(rs.getInt("season_year"), new Object[] { rs.getLong("id"), rs.getString("name"),
					rs.getObject("race_date", java.time.LocalDate.class) });
		}, circuit[0], distinct.toArray(Integer[]::new));
		var missing = distinct.stream().filter(y -> !races.containsKey(y)).toList();
		if (!missing.isEmpty()) {
			throw new BadRequestException(ref + " has no Grand Prix in " + missing.stream().map(String::valueOf)
					.reduce((x, y) -> x + ", " + y).orElseThrow());
		}
		List<CircuitComparison.Year> out = new ArrayList<>();
		for (int year : distinct) {
			var race = races.get(year);
			out.add(year(year, (Long) race[0], (String) race[1], (java.time.LocalDate) race[2]));
		}
		return new CircuitComparison(
				new CircuitComparison.Circuit((String) circuit[1], (String) circuit[2], seasons), out);
	}

	private CircuitComparison.Year year(int season, long raceId, String name, java.time.LocalDate date) {
		var winner = jdbc.query("""
				select e.id, d.ref, d.code, d.family_name, c.name as team
				from session_results sr join race_entries e on e.id = sr.race_entry_id join drivers d on d.id = e.driver_id
				join constructors c on c.id = e.constructor_id
				where e.race_id = ? and sr.session_type = 'R' and sr.position = 1 and sr.classified""",
				(rs, i) -> new Object[] { rs.getLong("id"), new CircuitComparison.Driver(rs.getString("ref"),
						trim(rs.getString("code")), rs.getString("family_name"), rs.getString("team")) },
				raceId).stream().findFirst().orElse(null);
		var fastest = jdbc.query("""
				select l.time_ms, l.lap, d.code from lap_times l join race_entries e on e.id = l.race_entry_id
				join drivers d on d.id = e.driver_id
				where e.race_id = ? and l.session_type = 'R' and l.time_ms is not null order by l.time_ms, l.lap limit 1""",
				(rs, i) -> new CircuitComparison.FastestLap(rs.getLong("time_ms"), rs.getInt("lap"),
						trim(rs.getString("code"))), raceId).stream().findFirst().orElse(null);
		var pace = jdbc.query("""
				select l.lap, round(percentile_cont(0.5) within group (order by l.time_ms)) as median_ms,
				       max(l.time_ms) filter (where l.race_entry_id = ?) as winner_ms
				from lap_times l join race_entries e on e.id = l.race_entry_id
				where e.race_id = ? and l.session_type = 'R' and l.time_ms is not null
				group by l.lap order by l.lap""", (rs, i) -> new LapPace(rs.getInt("lap"), longOrNull(rs, "median_ms"),
				longOrNull(rs, "winner_ms")), winner == null ? -1L : (Long) winner[0], raceId);
		var strategies = strategies(raceId);
		return new CircuitComparison.Year(season, raceId, name, date,
				winner == null ? null : (CircuitComparison.Driver) winner[1], fastest, pace, strategies.source(),
				strategies.cars());
	}

	private record Strategies(String source, List<Strategy> cars) {
	}

	// every car that started, in finishing order, with its stints: OpenF1's when the race has them, else
	// the laps between its pit stops (red-flag stoppages aren't stops)
	private Strategies strategies(long raceId) {
		record Car(long entry, Integer number, Integer position, String ref, String code, String surname, String team,
				int laps) {
		}
		var cars = jdbc.query("""
				select e.id, e.car_number, sr.position, d.ref, d.code, d.family_name, c.name as team,
				       coalesce(sr.laps_completed, 0) as laps
				from session_results sr join race_entries e on e.id = sr.race_entry_id join drivers d on d.id = e.driver_id
				join constructors c on c.id = e.constructor_id
				where e.race_id = ? and sr.session_type = 'R' and sr.status not in ('DID_NOT_START', 'DID_NOT_QUALIFY')
				order by sr.position nulls last""", (rs, i) -> new Car(rs.getLong("id"),
				rs.getObject("car_number", Integer.class), rs.getObject("position", Integer.class), rs.getString("ref"),
				trim(rs.getString("code")), rs.getString("family_name"), rs.getString("team"), rs.getInt("laps")), raceId);

		Map<Integer, List<Stint>> tyres = new LinkedHashMap<>();
		jdbc.query("""
				select t.driver_number, t.compound, t.lap_start, t.lap_end
				from tire_stints t join sessions s on s.id = t.session_id
				where s.race_id = ? and s.name = 'Race' and t.lap_start is not null and t.lap_end is not null
				order by t.driver_number, t.stint_number""", rs -> {
			tyres.computeIfAbsent(rs.getInt("driver_number"), n -> new ArrayList<>())
				.add(new Stint(rs.getString("compound"), rs.getInt("lap_start"), rs.getInt("lap_end")));
		}, raceId);
		if (!tyres.isEmpty()) {
			return new Strategies("TYRES", cars.stream().map(c -> {
				var stints = tyres.getOrDefault(c.number(), List.of());
				return new Strategy(c.position(), c.ref(), c.code(), c.surname(), c.team(), Math.max(0, stints.size() - 1),
						stints);
			}).toList());
		}

		Map<Long, List<Integer>> stops = new HashMap<>();
		jdbc.query("""
				select p.race_entry_id, p.lap from pit_stops p join race_entries e on e.id = p.race_entry_id
				where e.race_id = ? and p.lap is not null and p.lane_duration_ms <= ? order by p.lap""", rs -> {
			stops.computeIfAbsent(rs.getLong("race_entry_id"), e -> new ArrayList<>()).add(rs.getInt("lap"));
		}, raceId, RED_FLAG_MS);
		if (stops.isEmpty()) {
			return new Strategies("NONE", cars.stream()
				.map(c -> new Strategy(c.position(), c.ref(), c.code(), c.surname(), c.team(), 0, List.of()))
				.toList());
		}
		return new Strategies("PIT_STOPS", cars.stream().map(c -> {
			var laps = stops.getOrDefault(c.entry(), List.of());
			List<Stint> stints = new ArrayList<>();
			int from = 1;
			for (int lap : laps) {
				stints.add(new Stint(null, from, lap));
				from = lap + 1;
			}
			if (from <= c.laps()) {
				stints.add(new Stint(null, from, c.laps()));
			}
			return new Strategy(c.position(), c.ref(), c.code(), c.surname(), c.team(), laps.size(), stints);
		}).toList());
	}

	public DriverComparison drivers(String aRef, String bRef, int season) {
		if (aRef.equals(bRef)) {
			throw new BadRequestException("Compare two different drivers");
		}
		var a = driver(aRef);
		var b = driver(bRef);
		Map<Long, Result> aResults = results((Long) a[0], season);
		Map<Long, Result> bResults = results((Long) b[0], season);
		List<DriverComparison.Race> races = jdbc.query("""
				select id, round, name from races where season_year = ? order by round""",
				(rs, i) -> new Object[] { rs.getLong("id"), rs.getInt("round"), rs.getString("name") }, season)
			.stream()
			.filter(r -> aResults.containsKey((Long) r[0]) && bResults.containsKey((Long) r[0]))
			.map(r -> {
				var ra = aResults.get((Long) r[0]);
				var rb = bResults.get((Long) r[0]);
				Long gap = ra.medianLapMs() == null || rb.medianLapMs() == null ? null
						: ra.medianLapMs() - rb.medianLapMs();
				return new DriverComparison.Race((Integer) r[1], (Long) r[0], (String) r[2], ra, rb, gap);
			})
			.toList();
		return new DriverComparison(season, (DriverComparison.Driver) a[1], (DriverComparison.Driver) b[1],
				tally(races), races);
	}

	private Object[] driver(String ref) {
		return jdbc.query("select id, ref, code, given_name, family_name from drivers where ref = ?",
				(rs, i) -> new Object[] { rs.getLong("id"), new DriverComparison.Driver(rs.getString("ref"),
						trim(rs.getString("code")), rs.getString("given_name"), rs.getString("family_name")) },
				ref).stream().findFirst().orElseThrow(() -> new NotFoundException("Driver", ref));
	}

	// a driver's Grand Prix in a season, by race, with the median of their clean laps and their best lap
	private Map<Long, Result> results(long driver, int season) {
		Map<Long, Result> results = new HashMap<>();
		jdbc.query("""
				with season_laps as (
				  select e.race_id, e.driver_id, l.race_entry_id, l.lap, l.time_ms
				  from lap_times l join race_entries e on e.id = l.race_entry_id join races r on r.id = e.race_id
				  where r.season_year = ? and l.session_type = 'R' and l.time_ms is not null),
				clean as (
				  select * from season_laps l
				  where l.lap > 1 and not exists (select 1 from pit_stops p where p.race_entry_id = l.race_entry_id
				                                  and l.lap in (p.lap, p.lap + 1))),
				limits as (select race_id, min(time_ms) * 1.07 as limit_ms from clean group by race_id),
				medians as (
				  select c.race_id, round(percentile_cont(0.5) within group (order by c.time_ms)) as median_ms
				  from clean c join limits m on m.race_id = c.race_id
				  where c.driver_id = ? and c.time_ms <= m.limit_ms group by c.race_id),
				best as (select race_id, min(time_ms) as best_ms from season_laps where driver_id = ? group by race_id)
				select e.race_id, c.name as team, sr.grid, sr.position, coalesce(sr.classified, false) as classified,
				       sr.status, sr.status_detail, coalesce(sr.points, 0) as points, m.median_ms, b.best_ms
				from session_results sr join race_entries e on e.id = sr.race_entry_id join races r on r.id = e.race_id
				join constructors c on c.id = e.constructor_id
				left join medians m on m.race_id = e.race_id left join best b on b.race_id = e.race_id
				where r.season_year = ? and e.driver_id = ? and sr.session_type = 'R'""", rs -> {
			results.put(rs.getLong("race_id"), new Result(rs.getString("team"), rs.getObject("grid", Integer.class),
					rs.getObject("position", Integer.class), rs.getBoolean("classified"), rs.getString("status"),
					rs.getString("status_detail"), rs.getDouble("points"), longOrNull(rs, "median_ms"),
					longOrNull(rs, "best_ms")));
		}, season, driver, driver, season, driver);
		return results;
	}

	private static DriverComparison.Tally tally(List<DriverComparison.Race> races) {
		int aRace = 0, bRace = 0, aGrid = 0, bGrid = 0;
		double aPoints = 0, bPoints = 0;
		for (var race : races) {
			var a = race.a();
			var b = race.b();
			if (a.classified() && (!b.classified() || a.position() < b.position())) {
				aRace++;
			}
			else if (b.classified() && (!a.classified() || b.position() < a.position())) {
				bRace++;
			}
			if (a.grid() != null && b.grid() != null && a.grid() > 0 && b.grid() > 0) {
				if (a.grid() < b.grid()) {
					aGrid++;
				}
				else if (b.grid() < a.grid()) {
					bGrid++;
				}
			}
			aPoints += a.points();
			bPoints += b.points();
		}
		return new DriverComparison.Tally(races.size(), aRace, bRace, aGrid, bGrid, aPoints, bPoints);
	}

	private static Long longOrNull(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private static String trim(String code) {
		return code == null || code.isBlank() ? null : code.trim();
	}

}
