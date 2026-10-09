package com.telemetrix.racecontrol;

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

// One race-control message in a session, as OpenF1 sent it (RaceControlParser reads its meaning).
@Entity
@Table(name = "race_events")
public class RaceControlMessage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "session_id")
	private Session session;

	@Column(nullable = false)
	private Instant occurredAt;

	private Short lapNumber;

	private String category;

	private String flag;

	private String scope;

	private Short sector;

	private Short driverNumber;

	@Column(nullable = false)
	private String message;

	protected RaceControlMessage() {
	}

	public RaceControlMessage(Session session, Instant occurredAt, Integer lapNumber, String category, String flag,
			String scope, Integer sector, Integer driverNumber, String message) {
		this.session = session;
		this.occurredAt = occurredAt;
		this.lapNumber = lapNumber == null ? null : lapNumber.shortValue();
		this.category = category;
		this.flag = flag;
		this.scope = scope;
		this.sector = sector == null ? null : sector.shortValue();
		this.driverNumber = driverNumber == null ? null : driverNumber.shortValue();
		this.message = message;
	}

	public Long getId() {
		return id;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public Short getLapNumber() {
		return lapNumber;
	}

	public String getCategory() {
		return category;
	}

	public String getFlag() {
		return flag;
	}

	public String getScope() {
		return scope;
	}

	public Short getSector() {
		return sector;
	}

	public Short getDriverNumber() {
		return driverNumber;
	}

	public String getMessage() {
		return message;
	}

}
