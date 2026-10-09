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

// A stretch of a session under one track status (yellow, SC, VSC, VSC ending, red), with the leader's
// laps it started and ended on, and where it came from.
@Entity
@Table(name = "track_status")
public class SessionTrackStatus {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "session_id")
	private Session session;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TrackStatus status;

	@Column(nullable = false)
	private Instant startsAt;

	private Instant endsAt;

	private Short startLap;

	private Short endLap;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TrackStatusSource.Kind source;

	protected SessionTrackStatus() {
	}

	public SessionTrackStatus(Session session, TrackStatusPeriod period, Integer startLap, Integer endLap,
			TrackStatusSource.Kind source) {
		this.session = session;
		this.status = period.status();
		this.startsAt = period.from();
		this.endsAt = period.to();
		this.startLap = startLap == null ? null : startLap.shortValue();
		this.endLap = endLap == null ? null : endLap.shortValue();
		this.source = source;
	}

	public TrackStatus getStatus() {
		return status;
	}

	public Instant getStartsAt() {
		return startsAt;
	}

	public Instant getEndsAt() {
		return endsAt;
	}

	public Short getStartLap() {
		return startLap;
	}

	public Short getEndLap() {
		return endLap;
	}

	public TrackStatusSource.Kind getSource() {
		return source;
	}

}
