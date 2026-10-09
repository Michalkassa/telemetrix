package com.telemetrix.timing;

import com.telemetrix.race.Session;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;

// One lap of one car in a session. Its timing points are what the replay interpolates between
// (docs/adr/0004): lap start, end of sector 1, end of sector 2, lap end.
@Entity
@Table(name = "laps")
public class Lap {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "session_id")
	private Session session;

	// the car number in this race (OpenF1's driver_number)
	@Column(nullable = false)
	private Short driverNumber;

	@Column(nullable = false)
	private Short lapNumber;

	private Instant startsAt;

	@Column(name = "sector1_ms")
	private Integer sector1Ms;

	@Column(name = "sector2_ms")
	private Integer sector2Ms;

	@Column(name = "sector3_ms")
	private Integer sector3Ms;

	private Integer lapMs;

	@Column(name = "i1_speed")
	private Short i1Speed;

	@Column(name = "i2_speed")
	private Short i2Speed;

	private Short speedTrap;

	@Column(nullable = false)
	private boolean pitOutLap;

	protected Lap() {
	}

	public Lap(Session session, int driverNumber, int lapNumber, Instant startsAt) {
		this.session = session;
		this.driverNumber = (short) driverNumber;
		this.lapNumber = (short) lapNumber;
		this.startsAt = startsAt;
	}

	public void setTimes(Integer sector1Ms, Integer sector2Ms, Integer sector3Ms, Integer lapMs) {
		this.sector1Ms = sector1Ms;
		this.sector2Ms = sector2Ms;
		this.sector3Ms = sector3Ms;
		this.lapMs = lapMs;
	}

	public void setSpeeds(Short i1Speed, Short i2Speed, Short speedTrap) {
		this.i1Speed = i1Speed;
		this.i2Speed = i2Speed;
		this.speedTrap = speedTrap;
	}

	public void setPitOutLap(boolean pitOutLap) {
		this.pitOutLap = pitOutLap;
	}

	/** A start time and all three sectors: the lap gives 4 timing points. */
	public boolean isComplete() {
		return startsAt != null && sector1Ms != null && sector2Ms != null && sector3Ms != null;
	}

	/**
	 * Complete: start, end of S1, end of S2, end. Partial (a sector missing): start and end, if the lap
	 * time is known. No start time: none.
	 */
	public List<Instant> timingPoints() {
		if (startsAt == null) {
			return List.of();
		}
		if (isComplete()) {
			int end = lapMs != null ? lapMs : sector1Ms + sector2Ms + sector3Ms;
			return List.of(startsAt, startsAt.plusMillis(sector1Ms), startsAt.plusMillis(sector1Ms + sector2Ms),
					startsAt.plusMillis(end));
		}
		return lapMs != null ? List.of(startsAt, startsAt.plusMillis(lapMs)) : List.of(startsAt);
	}

	public Long getId() {
		return id;
	}

	public Session getSession() {
		return session;
	}

	public int getDriverNumber() {
		return driverNumber;
	}

	public int getLapNumber() {
		return lapNumber;
	}

	public Instant getStartsAt() {
		return startsAt;
	}

	public Integer getSector1Ms() {
		return sector1Ms;
	}

	public Integer getSector2Ms() {
		return sector2Ms;
	}

	public Integer getSector3Ms() {
		return sector3Ms;
	}

	public Integer getLapMs() {
		return lapMs;
	}

	public boolean isPitOutLap() {
		return pitOutLap;
	}

}
