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

// One set of tyres on one car, from lapStart to lapEnd.
@Entity
@Table(name = "tire_stints")
public class TireStint {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "session_id")
	private Session session;

	@Column(nullable = false)
	private Short driverNumber;

	@Column(nullable = false)
	private Short stintNumber;

	private String compound;

	private Short lapStart;

	private Short lapEnd;

	private Short tyreAgeAtStart;

	protected TireStint() {
	}

	public TireStint(Session session, int driverNumber, int stintNumber, String compound, Integer lapStart,
			Integer lapEnd, Integer tyreAgeAtStart) {
		this.session = session;
		this.driverNumber = (short) driverNumber;
		this.stintNumber = (short) stintNumber;
		this.compound = compound;
		this.lapStart = shortOrNull(lapStart);
		this.lapEnd = shortOrNull(lapEnd);
		this.tyreAgeAtStart = shortOrNull(tyreAgeAtStart);
	}

	private static Short shortOrNull(Integer value) {
		return value == null ? null : value.shortValue();
	}

	public int getDriverNumber() {
		return driverNumber;
	}

	public int getStintNumber() {
		return stintNumber;
	}

	public String getCompound() {
		return compound;
	}

	public Short getLapStart() {
		return lapStart;
	}

	public Short getLapEnd() {
		return lapEnd;
	}

	public Short getTyreAgeAtStart() {
		return tyreAgeAtStart;
	}

}
