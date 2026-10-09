package com.telemetrix.circuit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "circuits")
public class Circuit {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// Jolpica/Ergast circuit id, e.g. 'monza'
	@Column(nullable = false, unique = true)
	private String ref;

	@Column(nullable = false)
	private String name;

	private String locality;

	private String country;

	@Column(precision = 9, scale = 6)
	private BigDecimal latitude;

	@Column(precision = 9, scale = 6)
	private BigDecimal longitude;

	@Column(unique = true)
	private Integer openf1CircuitKey;

	protected Circuit() {
	}

	public Circuit(String ref, String name) {
		this.ref = ref;
		this.name = name;
	}

	// Takes the latest values from the source; returns whether anything changed.
	public boolean update(String name, String locality, String country, BigDecimal latitude, BigDecimal longitude) {
		boolean changed = !Objects.equals(this.name, name) || !Objects.equals(this.locality, locality)
				|| !Objects.equals(this.country, country) || !sameNumber(this.latitude, latitude)
				|| !sameNumber(this.longitude, longitude);
		if (changed) {
			this.name = name;
			this.locality = locality;
			this.country = country;
			this.latitude = latitude;
			this.longitude = longitude;
		}
		return changed;
	}

	public boolean linkOpenF1Circuit(Integer circuitKey) {
		boolean changed = !Objects.equals(this.openf1CircuitKey, circuitKey);
		this.openf1CircuitKey = circuitKey;
		return changed;
	}

	// 26.0325 and 26.032500 (as stored) are the same coordinate
	private static boolean sameNumber(BigDecimal a, BigDecimal b) {
		return a == null ? b == null : b != null && a.compareTo(b) == 0;
	}

	public Long getId() {
		return id;
	}

	public String getRef() {
		return ref;
	}

	public String getName() {
		return name;
	}

	public String getLocality() {
		return locality;
	}

	public String getCountry() {
		return country;
	}

	public BigDecimal getLatitude() {
		return latitude;
	}

	public BigDecimal getLongitude() {
		return longitude;
	}

	public Integer getOpenf1CircuitKey() {
		return openf1CircuitKey;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setLocality(String locality) {
		this.locality = locality;
	}

	public void setCountry(String country) {
		this.country = country;
	}

	public void setLatitude(BigDecimal latitude) {
		this.latitude = latitude;
	}

	public void setLongitude(BigDecimal longitude) {
		this.longitude = longitude;
	}

	public void setOpenf1CircuitKey(Integer openf1CircuitKey) {
		this.openf1CircuitKey = openf1CircuitKey;
	}

}
