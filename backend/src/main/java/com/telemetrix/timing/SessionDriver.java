package com.telemetrix.timing;

import com.telemetrix.race.Session;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;

// The driver of one car number in a session: the replay's dot label and colour.
@Entity
@Table(name = "session_drivers")
public class SessionDriver {

	@Embeddable
	public record Key(@Column(name = "session_id") Long sessionId, @Column(name = "driver_number") Short driverNumber)
			implements Serializable {
	}

	@EmbeddedId
	private Key id;

	// session_id is part of the key; this is the same column as an association (read-only)
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "session_id", insertable = false, updatable = false)
	private Session session;

	@Column(columnDefinition = "varchar(3)")
	private String code;

	private String fullName;

	private String teamName;

	@Column(columnDefinition = "varchar(6)")
	private String teamColour;

	private String headshotUrl;

	protected SessionDriver() {
	}

	public SessionDriver(Session session, int driverNumber, String code, String fullName, String teamName,
			String teamColour, String headshotUrl) {
		this.id = new Key(session.getId(), (short) driverNumber);
		this.session = session;
		this.code = code;
		this.fullName = fullName;
		this.teamName = teamName;
		this.teamColour = teamColour;
		this.headshotUrl = headshotUrl;
	}

	public int getDriverNumber() {
		return id.driverNumber();
	}

	public String getCode() {
		return code;
	}

	public String getFullName() {
		return fullName;
	}

	public String getTeamName() {
		return teamName;
	}

	public String getTeamColour() {
		return teamColour;
	}

	public String getHeadshotUrl() {
		return headshotUrl;
	}

}
