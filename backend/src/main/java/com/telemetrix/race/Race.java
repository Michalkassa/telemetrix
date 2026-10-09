package com.telemetrix.race;

import com.telemetrix.circuit.Circuit;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

// One Grand Prix weekend (an OpenF1 "meeting"). Owns its sessions: saving or deleting a race
// saves or deletes them too.
@Entity
@Table(name = "races")
public class Race {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "season_year")
	private Season season;

	@Column(nullable = false)
	private Short round;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "circuit_id")
	private Circuit circuit;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private LocalDate raceDate;

	@Column(unique = true)
	private Integer openf1MeetingKey;

	// When this race's data was fully downloaded (null: not yet, or the race hasn't happened yet)
	private Instant importedAt;

	@OneToMany(mappedBy = "race", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("startsAt")
	private List<Session> sessions = new ArrayList<>();

	protected Race() {
	}

	public Race(Season season, short round, Circuit circuit, String name, LocalDate raceDate) {
		this.season = season;
		this.round = round;
		this.circuit = circuit;
		this.name = name;
		this.raceDate = raceDate;
	}

	// Takes the latest calendar values; returns whether anything changed.
	public boolean update(String name, LocalDate raceDate, Circuit circuit) {
		boolean changed = !Objects.equals(this.name, name) || !Objects.equals(this.raceDate, raceDate)
				|| !Objects.equals(this.circuit.getId(), circuit.getId());
		this.name = name;
		this.raceDate = raceDate;
		this.circuit = circuit;
		return changed;
	}

	// Links this race to its OpenF1 meeting; returns whether the link changed.
	public boolean linkOpenF1Meeting(int meetingKey) {
		boolean changed = !Objects.equals(this.openf1MeetingKey, meetingKey);
		this.openf1MeetingKey = meetingKey;
		return changed;
	}

	public void markImported(Instant at) {
		this.importedAt = at;
	}

	// Keeps both sides of the relationship in step.
	public void addSession(Session session) {
		session.setRace(this);
		sessions.add(session);
	}

	public Long getId() {
		return id;
	}

	public Season getSeason() {
		return season;
	}

	public short getRound() {
		return round;
	}

	public Circuit getCircuit() {
		return circuit;
	}

	public String getName() {
		return name;
	}

	public LocalDate getRaceDate() {
		return raceDate;
	}

	public Integer getOpenf1MeetingKey() {
		return openf1MeetingKey;
	}

	public Instant getImportedAt() {
		return importedAt;
	}

	public List<Session> getSessions() {
		return Collections.unmodifiableList(sessions);
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setRaceDate(LocalDate raceDate) {
		this.raceDate = raceDate;
	}

	public void setOpenf1MeetingKey(Integer openf1MeetingKey) {
		this.openf1MeetingKey = openf1MeetingKey;
	}

}
