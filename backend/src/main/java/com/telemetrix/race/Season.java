package com.telemetrix.race;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// A championship year. The year itself is the id.
@Entity
@Table(name = "seasons")
public class Season {

	@Id
	private Short year;

	protected Season() {
	}

	public Season(short year) {
		this.year = year;
	}

	public short getYear() {
		return year;
	}

}
