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

// A team radio clip in a session: who and when, and the link to the recording on F1's archive.
@Entity
@Table(name = "team_radio")
public class TeamRadioClip {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "session_id")
	private Session session;

	@Column(nullable = false)
	private Short driverNumber;

	@Column(nullable = false)
	private Instant recordedAt;

	@Column(nullable = false)
	private String recordingUrl;

	protected TeamRadioClip() {
	}

	public TeamRadioClip(Session session, int driverNumber, Instant recordedAt, String recordingUrl) {
		this.session = session;
		this.driverNumber = (short) driverNumber;
		this.recordedAt = recordedAt;
		this.recordingUrl = recordingUrl;
	}

	public int getDriverNumber() {
		return driverNumber;
	}

	public Instant getRecordedAt() {
		return recordedAt;
	}

	public String getRecordingUrl() {
		return recordingUrl;
	}

}
