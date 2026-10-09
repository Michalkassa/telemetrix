package com.telemetrix.constructor;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.RepositoryTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RepositoryTest
class ConstructorRepositoryTest {

	@Autowired
	ConstructorRepository constructors;

	@Test
	void findsAConstructorByRef() {
		var redBull = new Constructor("red_bull", "Red Bull");
		redBull.setNationality("Austrian");
		constructors.save(redBull);

		assertThat(constructors.findByRef("red_bull")).hasValueSatisfying(c -> {
			assertThat(c.getName()).isEqualTo("Red Bull");
			assertThat(c.getNationality()).isEqualTo("Austrian");
		});
		assertThat(constructors.findByRef("ferrari")).isEmpty();
	}

}
