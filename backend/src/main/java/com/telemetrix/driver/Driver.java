package com.telemetrix.driver;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "drivers")
public class Driver {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// Jolpica/Ergast driver id, e.g. 'max_verstappen'
	@Column(nullable = false, unique = true)
	private String ref;

	// Three-letter code, e.g. 'VER'; missing for many early drivers
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 3)
	private String code;

	// Only since 2014
	private Short permanentNumber;

	@Column(nullable = false)
	private String givenName;

	@Column(nullable = false)
	private String familyName;

	private LocalDate dateOfBirth;

	private String nationality;

	protected Driver() {
	}

	public Driver(String ref, String givenName, String familyName) {
		this.ref = ref;
		this.givenName = givenName;
		this.familyName = familyName;
	}

	public Long getId() {
		return id;
	}

	public String getRef() {
		return ref;
	}

	public String getCode() {
		return code;
	}

	public Short getPermanentNumber() {
		return permanentNumber;
	}

	public String getGivenName() {
		return givenName;
	}

	public String getFamilyName() {
		return familyName;
	}

	public LocalDate getDateOfBirth() {
		return dateOfBirth;
	}

	public String getNationality() {
		return nationality;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public void setPermanentNumber(Short permanentNumber) {
		this.permanentNumber = permanentNumber;
	}

	public void setGivenName(String givenName) {
		this.givenName = givenName;
	}

	public void setFamilyName(String familyName) {
		this.familyName = familyName;
	}

	public void setDateOfBirth(LocalDate dateOfBirth) {
		this.dateOfBirth = dateOfBirth;
	}

	public void setNationality(String nationality) {
		this.nationality = nationality;
	}

}
