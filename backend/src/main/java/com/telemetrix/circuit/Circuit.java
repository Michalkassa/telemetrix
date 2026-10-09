package com.telemetrix.circuit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

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
