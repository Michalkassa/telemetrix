package com.telemetrix.constructor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// A team as entered in the championship, e.g. 'Red Bull'.
@Entity
@Table(name = "constructors")
public class Constructor {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// Jolpica/Ergast constructor id, e.g. 'red_bull'
	@Column(nullable = false, unique = true)
	private String ref;

	@Column(nullable = false)
	private String name;

	private String nationality;

	protected Constructor() {
	}

	public Constructor(String ref, String name) {
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

	public String getNationality() {
		return nationality;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setNationality(String nationality) {
		this.nationality = nationality;
	}

}
