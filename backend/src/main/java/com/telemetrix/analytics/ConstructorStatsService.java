package com.telemetrix.analytics;

import static com.telemetrix.analytics.DriverStatsService.RED_FLAG_MS;

import com.telemetrix.analytics.ConstructorStats.Constructor;
import com.telemetrix.common.NotFoundException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// A constructor's season, in SQL: medians with percentile_cont, gaps to the fastest team with window
// functions, the development trend's slope with regr_slope. Pace compares teams race by race (each team's
// median clean lap; each team's best qualifying lap of any segment), so the season figure is the median of
// those gaps. Lap times are in the history from 1996, qualifying segments from 2006 (one session before),
// pit stops from 2011.
@Service
@Transactional(readOnly = true)
public class ConstructorStatsService {

	// a team's race-pace gap to the fastest team in each of the season's races: clean laps only — not lap 1,
	// not a lap into or out of the pit lane, nothing over 107% of the race's fastest clean lap (safety cars)
	private static final String RACE_GAPS = """
			race_gaps as (
			  with laps as (
			    select e.race_id, e.constructor_id, l.time_ms
			    from lap_times l join race_entries e on e.id = l.race_entry_id join season_races r on r.id = e.race_id
			    where l.session_type = 'R' and l.lap > 1 and l.time_ms is not null
			      and not exists (select 1 from pit_stops p where p.race_entry_id = l.race_entry_id
			                      and l.lap in (p.lap, p.lap + 1))),
			  clean as (
			    select *, min(time_ms) over (partition by race_id) as fastest from laps),
			  teams as (
			    select race_id, constructor_id, percentile_cont(0.5) within group (order by time_ms) as median_ms
			    from clean where time_ms <= fastest * 1.07 group by race_id, constructor_id)
			  select race_id, constructor_id, (median_ms / min(median_ms) over (partition by race_id) - 1) * 100 as gap
			  from teams)""";

	// a team's qualifying gap in each race: its best lap of any segment, against the fastest of all
	private static final String QUALIFYING_GAPS = """
			qualifying_gaps as (
			  with teams as (
			    select q.race_id, q.constructor_id, min(least(q.q1_ms, q.q2_ms, q.q3_ms)) as best_ms
			    from qualifying q join season_races r on r.id = q.race_id
			    group by q.race_id, q.constructor_id having min(least(q.q1_ms, q.q2_ms, q.q3_ms)) is not null)
			  select race_id, constructor_id, (best_ms::float / min(best_ms) over (partition by race_id) - 1) * 100 as gap
			  from teams)""";

	private final JdbcTemplate jdbc;

	ConstructorStatsService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** A team's season; without a season, its latest. */
	public ConstructorStats stats(String ref, Integer season) {
		var team = jdbc.query("select id, ref, name from constructors where ref = ?",
				(rs, i) -> new Object[] { rs.getLong("id"), new Constructor(rs.getString("ref"), rs.getString("name")) },
				ref).stream().findFirst().orElseThrow(() -> new NotFoundException("Constructor", ref));
		long id = (Long) team[0];
		Integer year = jdbc.queryForObject("""
				select max(r.season_year) from race_entries e join races r on r.id = e.race_id
				where e.constructor_id = ? and (?::int is null or r.season_year = ?)""", Integer.class, id, season, season);
		if (year == null) {
			throw new NotFoundException("Constructor's season", ref + " " + (season == null ? "" : season));
		}
		return new ConstructorStats((Constructor) team[1], year, reliability(id, year), pitStops(id, year),
				pace(id, year), trend(id, year), strategies(id, year));
	}

	// a start is any race entry that wasn't a DNS/DNQ, a finish one still running at the end (a car that retired
	// with 90% run is classified, but a retirement); "Retired" alone (2023 on) says nothing of the cause
	private ConstructorStats.Reliability reliability(long team, int season) {
		var causes = jdbc.query("""
				select case when sr.status_detail = 'Retired' then 'Unknown' else sr.status_detail end as cause,
				       count(*) as count
				from session_results sr join race_entries e on e.id = sr.race_entry_id join races r on r.id = e.race_id
				where sr.session_type = 'R' and e.constructor_id = ? and r.season_year = ? and sr.status like 'RETIRED%'
				group by 1 order by 2 desc, 1""", (rs, i) -> new ConstructorStats.Cause(rs.getString("cause"),
				rs.getInt("count")), team, season);
		return jdbc.queryForObject("""
				select count(*) filter (where sr.status not in ('DID_NOT_START', 'DID_NOT_QUALIFY')) as starts,
				       count(*) filter (where sr.status in ('FINISHED', 'LAPPED')) as finishes,
				       count(*) filter (where sr.status like 'RETIRED%') as dnfs,
				       count(*) filter (where sr.status = 'RETIRED_MECHANICAL' and sr.status_detail <> 'Retired') as mechanical,
				       count(*) filter (where sr.status = 'RETIRED_INCIDENT' and sr.status_detail <> 'Retired') as incident,
				       count(*) filter (where sr.status like 'RETIRED%' and sr.status_detail = 'Retired') as unknown
				from session_results sr join race_entries e on e.id = sr.race_entry_id join races r on r.id = e.race_id
				where sr.session_type = 'R' and e.constructor_id = ? and r.season_year = ?""",
				(rs, i) -> new ConstructorStats.Reliability(rs.getInt("starts"), rs.getInt("finishes"), rs.getInt("dnfs"),
						rs.getInt("mechanical"), rs.getInt("incident"), rs.getInt("unknown"), causes),
				team, season);
	}

	// every team's median pit-lane time in the season, ranked (red-flag stoppages aren't stops)
	private ConstructorStats.PitStops pitStops(long team, int season) {
		return jdbc.query("""
				with stops as (
				  select e.constructor_id, p.lane_duration_ms
				  from pit_stops p join race_entries e on e.id = p.race_entry_id join races r on r.id = e.race_id
				  where r.season_year = ? and p.lane_duration_ms <= ?),
				teams as (
				  select constructor_id, count(*) as stops,
				         percentile_cont(0.5) within group (order by lane_duration_ms) / 1000.0 as median_s
				  from stops group by constructor_id),
				ranked as (
				  select *, rank() over (order by median_s)::int as rank, count(*) over () as teams from teams)
				select r.stops, r.median_s, r.rank, f.teams, f.field_s
				from (select count(*) as teams,
				             (select percentile_cont(0.5) within group (order by lane_duration_ms) / 1000.0 from stops) as field_s
				      from teams) f
				left join ranked r on r.constructor_id = ?""", (rs, i) -> new ConstructorStats.PitStops(rs.getInt("stops"),
				number(rs, "median_s"), (Integer) rs.getObject("rank", Integer.class), rs.getInt("teams"),
				number(rs, "field_s")), season, RED_FLAG_MS, team).getFirst();
	}

	private ConstructorStats.Pace pace(long team, int season) {
		return jdbc.queryForObject("with " + seasonRaces() + ", " + RACE_GAPS + ", " + QUALIFYING_GAPS + """
				select (select percentile_cont(0.5) within group (order by gap) from race_gaps where constructor_id = ?) as race,
				       (select percentile_cont(0.5) within group (order by gap) from qualifying_gaps where constructor_id = ?)
				         as qualifying""", (rs, i) -> new ConstructorStats.Pace(number(rs, "race"),
				number(rs, "qualifying")), season, team, team);
	}

	private ConstructorStats.Trend trend(long team, int season) {
		var rounds = jdbc.query("with " + seasonRaces() + ", " + RACE_GAPS + ", " + QUALIFYING_GAPS + """
				select r.round, r.name, q.gap as qualifying, g.gap as race
				from season_races r
				left join qualifying_gaps q on q.race_id = r.id and q.constructor_id = ?
				left join race_gaps g on g.race_id = r.id and g.constructor_id = ?
				where q.gap is not null or g.gap is not null
				order by r.round""", (rs, i) -> new ConstructorStats.Round(rs.getInt("round"), rs.getString("name"),
				number(rs, "qualifying"), number(rs, "race")), season, team, team);
		return jdbc.queryForObject("with " + seasonRaces() + ", " + RACE_GAPS + ", " + QUALIFYING_GAPS + """
				select (select regr_slope(q.gap, r.round) from qualifying_gaps q join season_races r on r.id = q.race_id
				        where q.constructor_id = ?) as qualifying,
				       (select regr_slope(g.gap, r.round) from race_gaps g join season_races r on r.id = g.race_id
				        where g.constructor_id = ?) as race""", (rs, i) -> new ConstructorStats.Trend(rounds,
				number(rs, "qualifying"), number(rs, "race")), season, team, team);
	}

	// positions gained by finishers who started from the grid, by how many times they stopped — in races with
	// pit-stop data (2011 on; before, every car would look like it never stopped)
	private List<ConstructorStats.Strategy> strategies(long team, int season) {
		return jdbc.query("""
				select (select count(*) from pit_stops p where p.race_entry_id = e.id and p.lane_duration_ms <= ?) as stops,
				       count(*) as finishes, avg(sr.grid - sr.position) as gained
				from session_results sr join race_entries e on e.id = sr.race_entry_id join races r on r.id = e.race_id
				where sr.session_type = 'R' and e.constructor_id = ? and r.season_year = ?
				  and sr.status in ('FINISHED', 'LAPPED') and sr.grid > 0 and sr.position is not null
				  and exists (select 1 from pit_stops p join race_entries o on o.id = p.race_entry_id where o.race_id = r.id)
				group by 1 order by 1""", (rs, i) -> new ConstructorStats.Strategy(rs.getInt("stops"),
				rs.getInt("finishes"), rs.getDouble("gained")), RED_FLAG_MS, team, season);
	}

	private static String seasonRaces() {
		return "season_races as (select id, round, name from races where season_year = ?)";
	}

	private static Double number(ResultSet rs, String column) throws SQLException {
		double value = rs.getDouble(column);
		return rs.wasNull() ? null : value;
	}

}
