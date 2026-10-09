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
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

// A penalty handed out during a session, read from the stewards' race-control message.
@Entity
@Table(name = "penalties")
public class SessionPenalty {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "session_id")
	private Session session;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "race_event_id")
	private RaceControlMessage message;

	@Column(nullable = false)
	private Short driverNumber;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PenaltyKind kind;

	private Short amount;

	private String reason;

	@Column(nullable = false)
	private Instant issuedAt;

	private Short lapNumber;

	protected SessionPenalty() {
	}

	/** amount: seconds (TIME, STOP_GO), places (GRID), or null. */
	public SessionPenalty(Session session, RaceControlMessage message, int driverNumber, PenaltyKind kind, Integer amount,
			String reason) {
		this.session = session;
		this.message = message;
		this.driverNumber = (short) driverNumber;
		this.kind = kind;
		this.amount = amount == null ? null : amount.shortValue();
		this.reason = reason;
		this.issuedAt = message.getOccurredAt();
		this.lapNumber = message.getLapNumber();
	}

	public RaceControlMessage getRaceControlMessage() {
		return message;
	}

	public int getDriverNumber() {
		return driverNumber;
	}

	public PenaltyKind getKind() {
		return kind;
	}

	public Short getAmount() {
		return amount;
	}

	public String getReason() {
		return reason;
	}

	public Instant getIssuedAt() {
		return issuedAt;
	}

	public Short getLapNumber() {
		return lapNumber;
	}

}
