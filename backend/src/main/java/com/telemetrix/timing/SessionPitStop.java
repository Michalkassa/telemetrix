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

// A pit stop in a session (OpenF1): the replay shows the car in the pit lane while it lasts.
@Entity
@Table(name = "session_pit_stops")
public class SessionPitStop {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "session_id")
	private Session session;

	@Column(nullable = false)
	private Short driverNumber;

	private Short lapNumber;

	private Instant enteredAt;

	private Integer laneDurationMs;

	private Integer stopDurationMs;

	protected SessionPitStop() {
	}

	public SessionPitStop(Session session, int driverNumber, Integer lapNumber, Instant enteredAt,
			Integer laneDurationMs, Integer stopDurationMs) {
		this.session = session;
		this.driverNumber = (short) driverNumber;
		this.lapNumber = lapNumber == null ? null : lapNumber.shortValue();
		this.enteredAt = enteredAt;
		this.laneDurationMs = laneDurationMs;
		this.stopDurationMs = stopDurationMs;
	}

	public int getDriverNumber() {
		return driverNumber;
	}

	public Short getLapNumber() {
		return lapNumber;
	}

	public Instant getEnteredAt() {
		return enteredAt;
	}

	public Integer getLaneDurationMs() {
		return laneDurationMs;
	}

	public Integer getStopDurationMs() {
		return stopDurationMs;
	}

}
