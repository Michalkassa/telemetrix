package com.telemetrix.championship;

import com.telemetrix.championship.SeasonPages.Cell;
import com.telemetrix.championship.SeasonPages.Line;
import com.telemetrix.championship.SeasonPages.RoundPoints;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// The Seasons lens: a season's results as a grid, and the drivers' points progression, both in the order
// of the latest standings (which also makes sure the season's snapshots are built).
@Service
public class SeasonPagesService {

	private final JdbcTemplate jdbc;

	private final ChampionshipService championship;

	SeasonPagesService(JdbcTemplate jdbc, ChampionshipService championship) {
		this.jdbc = jdbc;
		this.championship = championship;
	}

	@Transactional
	public SeasonPages.ResultsGrid resultsGrid(int season) {
		var standings = championship.standings(season, null); // 404 for an unknown season
		var races = jdbc.query("""
				select id, round, name, race_date from races where season_year = ? order by round""",
				(rs, i) -> new SeasonPages.Race(rs.getInt("round"), rs.getLong("id"), rs.getString("name"),
						rs.getObject("race_date", java.time.LocalDate.class)), season);
		Map<String, List<Cell>> cells = new HashMap<>();
		jdbc.query("""
				select d.ref, r.round, r.id as race_id, sr.position, sr.classified, sr.points, sr.status
				from session_results sr join race_entries e on e.id = sr.race_entry_id
				join races r on r.id = e.race_id join drivers d on d.id = e.driver_id
				where r.season_year = ? and sr.session_type = 'R' order by r.round""", rs -> {
			boolean classified = !Boolean.FALSE.equals(rs.getObject("classified", Boolean.class));
			cells.computeIfAbsent(rs.getString("ref"), k -> new ArrayList<>()).add(new Cell(rs.getInt("round"),
					rs.getLong("race_id"), classified ? rs.getObject("position", Integer.class) : null,
					rs.getBigDecimal("points") == null ? 0 : rs.getBigDecimal("points").doubleValue(),
					rs.getString("status")));
		}, season);
		var rows = standings.drivers().stream()
				.map(d -> new SeasonPages.Row(d.position(), d.ref(), d.code(), d.surname(), d.points(),
						cells.getOrDefault(d.ref(), List.of())))
				.toList();
		return new SeasonPages.ResultsGrid(season, races, rows);
	}

	@Transactional
	public SeasonPages.Progression progression(int season) {
		var standings = championship.standings(season, null); // builds the snapshots; 404 for an unknown season
		Map<String, List<RoundPoints>> rounds = new LinkedHashMap<>();
		jdbc.query("""
				select ref, round, points from championship_snapshots
				where season = ? and kind = 'DRIVER' order by round""", rs -> {
			rounds.computeIfAbsent(rs.getString("ref"), k -> new ArrayList<>())
					.add(new RoundPoints(rs.getInt("round"), rs.getDouble("points")));
		}, season);
		List<Line> lines = standings.drivers().stream()
				.map(d -> new Line(d.ref(), d.code(), d.surname(), rounds.getOrDefault(d.ref(), List.of())))
				.toList();
		return new SeasonPages.Progression(season, lines);
	}

}
