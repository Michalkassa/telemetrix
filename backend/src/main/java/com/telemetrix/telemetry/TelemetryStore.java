package com.telemetrix.telemetry;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

// Telemetry tables through plain JDBC: ~35,000 rows per car and race are written in batches, which
// JPA entities would only slow down.
@Repository
class TelemetryStore {

	private final JdbcTemplate jdbc;

	TelemetryStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	boolean isDownloaded(long sessionId, int driver) {
		return Boolean.TRUE.equals(jdbc.queryForObject("""
				select exists (select 1 from telemetry_downloads where session_id = ? and driver_number = ?)""",
				Boolean.class, sessionId, driver));
	}

	@Transactional
	public void save(long sessionId, int driver, TelemetryMerger.Result result, Instant downloadedAt) {
		jdbc.batchUpdate("""
				insert into telemetry_points (session_id, driver_number, recorded_at, speed, throttle, brake, gear, rpm,
				    drs, x, y, z)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) on conflict do nothing""", result.points(), 1000,
				(ps, p) -> {
					ps.setLong(1, sessionId);
					ps.setInt(2, driver);
					ps.setObject(3, utc(p.recordedAt()));
					ps.setObject(4, p.speed());
					ps.setObject(5, p.throttle());
					ps.setObject(6, p.brake());
					ps.setObject(7, p.gear());
					ps.setObject(8, p.rpm());
					ps.setObject(9, p.drs());
					ps.setObject(10, p.x());
					ps.setObject(11, p.y());
					ps.setObject(12, p.z());
				});
		jdbc.batchUpdate("""
				insert into telemetry_gaps (session_id, driver_number, gap_from, gap_to) values (?, ?, ?, ?)
				on conflict do nothing""", result.gaps(), 1000, (ps, g) -> {
			ps.setLong(1, sessionId);
			ps.setInt(2, driver);
			ps.setObject(3, utc(g.from()));
			ps.setObject(4, utc(g.to()));
		});
		jdbc.update("""
				insert into telemetry_downloads (session_id, driver_number, downloaded_at, point_count, dropped_count)
				values (?, ?, ?, ?, ?) on conflict do nothing""", sessionId, driver, utc(downloadedAt),
				result.points().size(), result.dropped());
	}

	/** Points from `from` (inclusive) to `to` (exclusive), in time order. */
	List<TelemetryPoint> points(long sessionId, int driver, Instant from, Instant to) {
		return jdbc.query("""
				select recorded_at, speed, throttle, brake, gear, rpm, drs, x, y, z from telemetry_points
				where session_id = ? and driver_number = ? and recorded_at >= ? and recorded_at < ?
				order by recorded_at""", (rs, n) -> new TelemetryPoint(instant(rs, "recorded_at"), integer(rs, "speed"),
				integer(rs, "throttle"), integer(rs, "brake"), integer(rs, "gear"), integer(rs, "rpm"), integer(rs, "drs"),
				integer(rs, "x"), integer(rs, "y"), integer(rs, "z")), sessionId, driver, utc(from), utc(to));
	}

	/** Gaps that overlap the window. */
	List<TelemetryGap> gaps(long sessionId, int driver, Instant from, Instant to) {
		return jdbc.query("""
				select gap_from, gap_to from telemetry_gaps
				where session_id = ? and driver_number = ? and gap_from < ? and gap_to > ?
				order by gap_from""", (rs, n) -> new TelemetryGap(instant(rs, "gap_from"), instant(rs, "gap_to")),
				sessionId, driver, utc(to), utc(from));
	}

	private static OffsetDateTime utc(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		return rs.getObject(column, OffsetDateTime.class).toInstant();
	}

	private static Integer integer(ResultSet rs, String column) throws SQLException {
		int value = rs.getInt(column);
		return rs.wasNull() ? null : value;
	}

}
