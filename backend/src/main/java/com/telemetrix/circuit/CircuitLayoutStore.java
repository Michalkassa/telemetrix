package com.telemetrix.circuit;

import com.telemetrix.circuit.TrackOutline.Point;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

// circuit_layouts through JDBC: the outline is two integer arrays, the corners a JSON list.
@Repository
public class CircuitLayoutStore {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final JdbcTemplate jdbc;

	CircuitLayoutStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public boolean exists(long circuitId, int year) {
		return Boolean.TRUE.equals(jdbc.queryForObject(
				"select exists (select 1 from circuit_layouts where circuit_id = ? and year = ?)", Boolean.class,
				circuitId, year));
	}

	public Optional<CircuitLayout> find(long circuitId, int year) {
		return jdbc.query(SELECT + " where l.circuit_id = ? and l.year = ?", this::layout, circuitId, year).stream()
				.findFirst();
	}

	public Optional<CircuitLayout> findByRef(String circuitRef, int year) {
		return jdbc.query(SELECT + " where c.ref = ? and l.year = ?", this::layout, circuitRef, year).stream()
				.findFirst();
	}

	public void save(long circuitId, int year, CircuitLayout layout) {
		Integer[] xs = layout.points().stream().map(p -> (int) Math.round(p.x())).toArray(Integer[]::new);
		Integer[] ys = layout.points().stream().map(p -> (int) Math.round(p.y())).toArray(Integer[]::new);
		double[] ends = layout.sectorEnds();
		jdbc.update(con -> {
			var ps = con.prepareStatement("""
					insert into circuit_layouts (circuit_id, year, source, rotation, x, y, sector1_end, sector2_end,
					    sector_ends_from, corners)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
					on conflict (circuit_id, year) do nothing""");
			ps.setLong(1, circuitId);
			ps.setInt(2, year);
			ps.setString(3, layout.source().name());
			ps.setDouble(4, layout.rotation());
			ps.setArray(5, con.createArrayOf("integer", xs));
			ps.setArray(6, con.createArrayOf("integer", ys));
			ps.setObject(7, ends == null ? null : ends[0]);
			ps.setObject(8, ends == null ? null : ends[1]);
			ps.setString(9, layout.sectorEndsSource() == null ? null : layout.sectorEndsSource().name());
			ps.setString(10, JSON.writeValueAsString(layout.corners()));
			return ps;
		});
	}

	private static final String SELECT = """
			select c.ref, l.year, l.source, l.rotation, l.x, l.y, l.sector1_end, l.sector2_end, l.sector_ends_from,
			       l.corners
			from circuit_layouts l join circuits c on c.id = l.circuit_id""";

	private CircuitLayout layout(ResultSet rs, int row) throws SQLException {
		Integer[] xs = ints(rs.getArray("x"));
		Integer[] ys = ints(rs.getArray("y"));
		List<Point> points = new ArrayList<>(xs.length);
		for (int i = 0; i < xs.length; i++) {
			points.add(new Point(xs[i], ys[i]));
		}
		double s1 = rs.getDouble("sector1_end");
		boolean hasEnds = !rs.wasNull();
		double s2 = rs.getDouble("sector2_end");
		String endsFrom = rs.getString("sector_ends_from");
		List<CircuitLayout.Corner> corners = JSON.readValue(rs.getString("corners"),
				new TypeReference<List<CircuitLayout.Corner>>() {
				});
		return new CircuitLayout(rs.getString("ref"), rs.getInt("year"), CircuitLayout.Source.valueOf(rs.getString("source")),
				rs.getDouble("rotation"), points, hasEnds ? new double[] { s1, s2 } : null,
				endsFrom == null ? null : CircuitLayout.SectorEndsSource.valueOf(endsFrom), corners);
	}

	private static Integer[] ints(Array array) throws SQLException {
		return (Integer[]) array.getArray();
	}

}
