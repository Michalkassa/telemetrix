package com.telemetrix.driver;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.RepositoryTest;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@RepositoryTest
class DriverRepositoryTest {

	@Autowired
	DriverRepository drivers;

	@Autowired
	TestEntityManager em;

	@Test
	void findsADriverByRefWithAllItsFields() {
		var max = new Driver("max_verstappen", "Max", "Verstappen");
		max.setCode("VER");
		max.setPermanentNumber((short) 1);
		max.setDateOfBirth(LocalDate.parse("1997-09-30"));
		max.setNationality("Dutch");
		drivers.save(max);
		em.flush();
		em.clear();

		var found = drivers.findByRef("max_verstappen").orElseThrow();

		assertThat(found.getCode()).isEqualTo("VER");
		assertThat(found.getPermanentNumber()).isEqualTo((short) 1);
		assertThat(found.getGivenName()).isEqualTo("Max");
		assertThat(found.getFamilyName()).isEqualTo("Verstappen");
		assertThat(found.getDateOfBirth()).isEqualTo("1997-09-30");
		assertThat(found.getNationality()).isEqualTo("Dutch");
		assertThat(drivers.findByRef("hamilton")).isEmpty();
	}

	@Test
	void earlyDriversHaveNoCodeOrNumber() {
		drivers.save(new Driver("fangio", "Juan", "Fangio"));
		em.flush();
		em.clear();

		var found = drivers.findByRef("fangio").orElseThrow();

		assertThat(found.getCode()).isNull();
		assertThat(found.getPermanentNumber()).isNull();
	}

}
