package com.telemetrix.race;

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
import java.util.Objects;

// One session of a weekend: 'Practice 1', 'Qualifying', 'Sprint', 'Race', ...
// Created through Race.addSession(...).
@Entity
@Table(name = "sessions")
public class Session {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "race_id")
	private Race race;

	@Column(unique = true)
	private Integer openf1SessionKey;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private Instant startsAt;

	private Instant endsAt;

	protected Session() {
	}

	public Session(String name, Instant startsAt) {
		this.name = name;
		this.startsAt = startsAt;
	}

	// Takes the latest values from the source; returns whether anything changed.
	public boolean update(String name, Instant startsAt, Instant endsAt) {
		boolean changed = !Objects.equals(this.name, name) || !Objects.equals(this.startsAt, startsAt)
				|| !Objects.equals(this.endsAt, endsAt);
		this.name = name;
		this.startsAt = startsAt;
		this.endsAt = endsAt;
		return changed;
	}

	void setRace(Race race) {
		this.race = race;
	}

	public Long getId() {
		return id;
	}

	public Race getRace() {
		return race;
	}

	public Integer getOpenf1SessionKey() {
		return openf1SessionKey;
	}

	public String getName() {
		return name;
	}

	public Instant getStartsAt() {
		return startsAt;
	}

	public Instant getEndsAt() {
		return endsAt;
	}

	public void setOpenf1SessionKey(Integer openf1SessionKey) {
		this.openf1SessionKey = openf1SessionKey;
	}

	public void setStartsAt(Instant startsAt) {
		this.startsAt = startsAt;
	}

	public void setEndsAt(Instant endsAt) {
		this.endsAt = endsAt;
	}

}
