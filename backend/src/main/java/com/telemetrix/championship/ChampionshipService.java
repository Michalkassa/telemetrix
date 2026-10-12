package com.telemetrix.championship;

import com.telemetrix.championship.ResultLine.Kind;
import com.telemetrix.common.NotFoundException;
import com.telemetrix.eventstore.Projection;
import com.telemetrix.eventstore.StreamAppended;
import com.telemetrix.eventstore.StreamType;
import com.telemetrix.ingestion.history.HistoryImported;
import com.telemetrix.result.ResultsRecorder;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Championship standings, round by round: a read model of the races' results streams (docs/adr/0006) —
// the points awarded, Grand Prix and sprints — in championship_snapshots, one table per round. A season is
// built when first asked for, and built again after one of its races' streams gets a new generation.
@Service
public class ChampionshipService implements Projection {

	private final JdbcTemplate jdbc;

	private final ResultsRecorder recorder;

	ChampionshipService(JdbcTemplate jdbc, ResultsRecorder recorder) {
		this.jdbc = jdbc;
		this.recorder = recorder;
	}

	@Override
	public String name() {
		return "championship_snapshots";
	}

	/** Drops every season's standings: each is built again from the streams when next asked for. */
	@Override
	@Transactional
	public void rebuild() {
		jdbc.update("delete from championship_snapshots");
	}

	/** The history changed: every season's standings are worked out again when next asked for. */
	@EventListener
	@Transactional
	public void onHistoryImported(HistoryImported imported) {
		rebuild();
	}

	/** A race's results changed: its season is worked out again (in the same transaction as the append). */
	@EventListener
	public void onStreamAppended(StreamAppended appended) {
		if (appended.stream().type() == StreamType.RACE_RESULTS) {
			jdbc.update("delete from championship_snapshots where season = (select season_year from races where id = ?)",
					appended.stream().id());
		}
	}

	/** The standings after `round` (null: the latest). */
	@Transactional
	public ChampionshipStandings standings(int season, Integer round) {
		if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from seasons where year = ?)",
				Boolean.class, season))) {
			throw new NotFoundException("Season", season);
		}
		int rounds = ensureSnapshots(season);
		int shown = round == null ? rounds : Math.min(Math.max(round, 0), rounds);
		var drivers = jdbc.query("""
				select s.position, s.ref, d.code, d.given_name, d.family_name, s.points, s.wins,
				       (select c.name from race_entries e join races r on r.id = e.race_id
				        join constructors c on c.id = e.constructor_id
				        where e.driver_id = d.id and r.season_year = s.season and r.round <= s.round
				        order by r.round desc limit 1) as team
				from championship_snapshots s join drivers d on d.ref = s.ref
				where s.season = ? and s.round = ? and s.kind = 'DRIVER' order by s.position""",
				(rs, i) -> new ChampionshipStandings.Driver(rs.getInt("position"), rs.getString("ref"),
						trim(rs.getString("code")), rs.getString("given_name"), rs.getString("family_name"),
						rs.getString("team"), rs.getDouble("points"), rs.getInt("wins")), season, shown);
		var teams = jdbc.query("""
				select s.position, s.ref, c.name, s.points, s.wins
				from championship_snapshots s join constructors c on c.ref = s.ref
				where s.season = ? and s.round = ? and s.kind = 'CONSTRUCTOR' order by s.position""",
				(rs, i) -> new ChampionshipStandings.Team(rs.getInt("position"), rs.getString("ref"), rs.getString("name"),
						rs.getDouble("points"), rs.getInt("wins")), season, shown);
		return new ChampionshipStandings(season, shown, rounds, drivers, teams);
	}

	// builds the season's snapshots unless they're built; returns the number of rounds
	private int ensureSnapshots(int season) {
		Integer built = jdbc.queryForObject("select max(round) from championship_snapshots where season = ?",
				Integer.class, season);
		if (built != null) {
			return built;
		}
		recorder.recordMissing(season); // races opened before there was a store
		List<ResultLine> lines = results(season);
		int rounds = lines.stream().mapToInt(ResultLine::round).max().orElse(0);
		List<Object[]> rows = new ArrayList<>();
		for (int round : lines.stream().mapToInt(ResultLine::round).distinct().sorted().toArray()) {
			var standings = StandingsCalculator.compute(season, lines.stream().filter(l -> l.round() <= round).toList());
			standings.drivers().forEach(s -> rows.add(row(season, round, "DRIVER", s)));
			standings.constructors().forEach(s -> rows.add(row(season, round, "CONSTRUCTOR", s)));
		}
		jdbc.batchUpdate("""
				insert into championship_snapshots (season, round, kind, ref, position, points, wins)
				values (?, ?, ?, ?, ?, ?, ?)""", rows);
		return rounds;
	}

	private static Object[] row(int season, int round, String kind, Standing s) {
		return new Object[] { season, round, kind, s.id(), s.position(), s.points(), s.wins() };
	}

	/** The season's Grand Prix and sprint results, with the points awarded: its races' RESULT_RECORDED events. */
	List<ResultLine> results(int season) {
		return jdbc.query("""
				select r.round, ev.payload ->> 'sessionType' as session_type, ev.payload ->> 'driverRef' as driver,
				       ev.payload ->> 'constructorRef' as constructor, (ev.payload ->> 'position')::int as position,
				       (ev.payload ->> 'classified')::boolean as classified, (ev.payload ->> 'points')::numeric as points
				from current_events ev join races r on r.id = ev.stream_id
				where ev.stream_type = 'RACE_RESULTS' and ev.type = 'RESULT_RECORDED' and r.season_year = ?""",
				(rs, i) -> new ResultLine(rs.getInt("round"), rs.getString("session_type").equals("SR") ? Kind.SPRINT
						: Kind.GRAND_PRIX, rs.getString("driver"), rs.getString("constructor"),
						// count-back counts finishing places only; the history keeps one for retired cars too
						Boolean.FALSE.equals(rs.getObject("classified", Boolean.class)) ? null
								: rs.getObject("position", Integer.class),
						rs.getBigDecimal("points") == null ? 0 : rs.getBigDecimal("points").doubleValue()), season);
	}

	private static String trim(String code) {
		return code == null || code.isBlank() ? null : code.trim();
	}

}
