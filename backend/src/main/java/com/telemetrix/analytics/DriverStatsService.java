package com.telemetrix.analytics;

import com.telemetrix.analytics.DriverStats.Career;
import com.telemetrix.analytics.DriverStats.Conditions;
import com.telemetrix.common.NotFoundException;
import com.telemetrix.eventstore.Projection;
import com.telemetrix.result.ResultsRecorder;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// A driver's statistics, in SQL: aggregates with FILTER over the driver_race_results materialized view
// (one row per Grand Prix entry; a read model of the races' results streams, docs/adr/0006), window
// functions for shares and running totals. Poles count race grid
// position 1, not qualifying P1 (the qualifying view is the order before grid penalties).
@Service
@Transactional(readOnly = true)
public class DriverStatsService implements Projection {

	/** Longer than this in the pit lane is a red-flag stoppage, not a pit stop. */
	public static final long RED_FLAG_MS = 5 * 60_000;

	private final JdbcTemplate jdbc;

	private final ResultsRecorder results;

	DriverStatsService(JdbcTemplate jdbc, ResultsRecorder results) {
		this.jdbc = jdbc;
		this.results = results;
	}

	@Override
	public String name() {
		return "driver_race_results";
	}

	/** Works the view out again from the results streams alone. */
	@Override
	@Transactional
	public void rebuild() {
		jdbc.execute("refresh materialized view driver_race_results");
	}

	/** After the history import: records every race's results that changed, then rebuilds the view. */
	@Transactional
	public void refresh() {
		results.recordAll();
		rebuild();
	}

	/** After a race import (which brings its weather): records that race again, then rebuilds the view. */
	@Transactional
	public void refresh(long raceId) {
		results.record(raceId);
		rebuild();
	}

	public DriverStats stats(String ref) {
		var driver = jdbc.query("select id, ref, code, given_name, family_name from drivers where ref = ?",
				(rs, i) -> new Object[] { rs.getLong("id"), new DriverStats.Driver(rs.getString("ref"),
						trim(rs.getString("code")), rs.getString("given_name"), rs.getString("family_name")) }, ref)
				.stream().findFirst().orElseThrow(() -> new NotFoundException("Driver", ref));
		long id = (Long) driver[0];
		return new DriverStats((DriverStats.Driver) driver[1], career(id), wetDry(id), tracks(id), pitStops(id),
				tyres(id), seasons(id));
	}

	/** Every driver who has raced, the latest season first, then by surname. */
	public java.util.List<DriverSummary> directory() {
		return jdbc.query("""
				select d.ref, d.code, d.given_name, d.family_name, d.nationality, min(r.season) as first_season,
				       max(r.season) as last_season, count(*) filter (where r.started) as starts,
				       count(*) filter (where r.classified and r.position = 1) as wins
				from driver_race_results r join drivers d on d.id = r.driver_id
				group by d.id order by last_season desc, d.family_name, d.given_name""",
				(rs, i) -> new DriverSummary(rs.getString("ref"), trim(rs.getString("code")), rs.getString("given_name"),
						rs.getString("family_name"), rs.getString("nationality"), rs.getInt("first_season"),
						rs.getInt("last_season"), rs.getInt("starts"), rs.getInt("wins")));
	}

	/** Every Grand Prix a driver entered, the newest first. */
	public java.util.List<RaceLogEntry> raceLog(String ref) {
		Long id = jdbc.query("select id from drivers where ref = ?", (rs, i) -> rs.getLong("id"), ref).stream()
				.findFirst().orElseThrow(() -> new NotFoundException("Driver", ref));
		return jdbc.query("""
				select r.season_year, r.round, r.id as race_id, r.name as race, c.ref as team_ref, c.name as team,
				       sr.grid, sr.position, coalesce(sr.classified, false) as classified, sr.status, sr.status_detail,
				       coalesce(sr.points, 0) as points
				from session_results sr join race_entries e on e.id = sr.race_entry_id join races r on r.id = e.race_id
				join constructors c on c.id = e.constructor_id
				where sr.session_type = 'R' and e.driver_id = ?
				order by r.season_year desc, r.round desc""", (rs, i) -> new RaceLogEntry(rs.getInt("season_year"),
				rs.getInt("round"), rs.getLong("race_id"), rs.getString("race"), rs.getString("team_ref"),
				rs.getString("team"), rs.getObject("grid", Integer.class), rs.getObject("position", Integer.class),
				rs.getBoolean("classified"), rs.getString("status"), rs.getString("status_detail"),
				rs.getDouble("points")), id);
	}

	private Career career(long driver) {
		return jdbc.queryForObject("""
				select count(*) filter (where started) as starts,
				       count(*) filter (where classified and position = 1) as wins,
				       count(*) filter (where classified and position <= 3) as podiums,
				       count(*) filter (where dnf) as dnfs,
				       count(*) filter (where grid = 1) as poles,
				       count(*) filter (where fastest_lap_rank = 1) as fastest_laps,
				       avg(position) filter (where classified) as average_finish,
				       coalesce(sum(positions_gained), 0) as positions_gained,
				       avg(positions_gained) as average_positions_gained,
				       coalesce(sum(points), 0) as points
				from driver_race_results where driver_id = ?""", (rs, i) -> new Career(rs.getInt("starts"),
				rs.getInt("wins"), rs.getInt("podiums"), rs.getInt("dnfs"), rs.getInt("poles"), rs.getInt("fastest_laps"),
				decimal(rs, "average_finish"), rs.getInt("positions_gained"), decimal(rs, "average_positions_gained"),
				rs.getDouble("points")), driver);
	}

	// races with known weather only (OpenF1, 2023 on)
	private DriverStats.WetDry wetDry(long driver) {
		var conditions = jdbc.query("""
				select wet, count(*) as races,
				       count(*) filter (where classified and position = 1) as wins,
				       count(*) filter (where classified and position <= 3) as podiums,
				       avg(position) filter (where classified) as average_finish
				from driver_race_results where driver_id = ? and started and wet is not null
				group by wet""", (rs, i) -> new Object[] { rs.getBoolean("wet"), new Conditions(rs.getInt("races"),
				rs.getInt("wins"), rs.getInt("podiums"), decimal(rs, "average_finish")) }, driver);
		Conditions none = new Conditions(0, 0, 0, null);
		Conditions wet = none;
		Conditions dry = none;
		for (Object[] c : conditions) {
			if ((Boolean) c[0]) {
				wet = (Conditions) c[1];
			}
			else {
				dry = (Conditions) c[1];
			}
		}
		return new DriverStats.WetDry(wet, dry);
	}

	private java.util.List<DriverStats.Track> tracks(long driver) {
		return jdbc.query("""
				select c.ref, c.name, count(*) filter (where d.started) as starts,
				       count(*) filter (where d.classified and d.position = 1) as wins,
				       count(*) filter (where d.classified and d.position <= 3) as podiums,
				       min(d.position) filter (where d.classified) as best_finish,
				       avg(d.position) filter (where d.classified) as average_finish
				from driver_race_results d join circuits c on c.id = d.circuit_id
				where d.driver_id = ? group by c.ref, c.name
				having count(*) filter (where d.started) > 0
				order by starts desc, wins desc, c.ref""", (rs, i) -> new DriverStats.Track(rs.getString("ref"),
				rs.getString("name"), rs.getInt("starts"), rs.getInt("wins"), rs.getInt("podiums"),
				rs.getObject("best_finish", Integer.class), decimal(rs, "average_finish")), driver);
	}

	// the history's pit stops (2011 on): how many, a race, and the time in the pit lane. A red flag puts the
	// cars in the pit lane for 6 to 50 minutes, and the history records that as a stop: anything over 5 minutes
	// isn't one (real stops, repairs too, take under 3 — there's nothing in between in the data)
	private DriverStats.PitStops pitStops(long driver) {
		return jdbc.queryForObject("""
				select count(*) as stops, count(distinct p.race_entry_id) as races,
				       avg(p.lane_duration_ms) / 1000.0 as average_lane_seconds
				from pit_stops p join race_entries e on e.id = p.race_entry_id
				where e.driver_id = ? and p.lane_duration_ms <= ?""", (rs, i) -> {
			int stops = rs.getInt("stops");
			int races = rs.getInt("races");
			return new DriverStats.PitStops(stops, races == 0 ? null : (double) stops / races,
					decimal(rs, "average_lane_seconds"));
		}, driver, RED_FLAG_MS);
	}

	// tyre stints in races (OpenF1, 2023 on): laps on each compound, and its share of all laps (a window)
	private java.util.List<DriverStats.Tyre> tyres(long driver) {
		return jdbc.query("""
				select t.compound, count(*) as stints, sum(t.lap_end - t.lap_start + 1) as laps,
				       sum(t.lap_end - t.lap_start + 1)::float / sum(sum(t.lap_end - t.lap_start + 1)) over () as share,
				       avg(t.lap_end - t.lap_start + 1) as average_stint_laps
				from tire_stints t
				join sessions s on s.id = t.session_id and s.name = 'Race'
				join race_entries e on e.race_id = s.race_id and e.car_number = t.driver_number
				where e.driver_id = ? and t.compound is not null and t.lap_start is not null and t.lap_end is not null
				group by t.compound order by laps desc, t.compound""", (rs, i) -> new DriverStats.Tyre(
				rs.getString("compound"), rs.getInt("stints"), rs.getInt("laps"), rs.getDouble("share"),
				rs.getDouble("average_stint_laps")), driver);
	}

	// each season, with the career's wins so far (a running total: a window over the seasons)
	private java.util.List<DriverStats.Season> seasons(long driver) {
		return jdbc.query("""
				select season, starts, wins, points, sum(wins) over (order by season) as career_wins
				from (select season, count(*) filter (where started) as starts,
				             count(*) filter (where classified and position = 1) as wins, sum(points) as points
				      from driver_race_results where driver_id = ? group by season) per_season
				order by season""", (rs, i) -> new DriverStats.Season(rs.getInt("season"), rs.getInt("starts"),
				rs.getInt("wins"), rs.getDouble("points"), rs.getInt("career_wins")), driver);
	}

	private static Double decimal(ResultSet rs, String column) throws SQLException {
		var value = rs.getBigDecimal(column);
		return value == null ? null : value.doubleValue();
	}

	private static String trim(String code) {
		return code == null || code.isBlank() ? null : code.trim();
	}

}
