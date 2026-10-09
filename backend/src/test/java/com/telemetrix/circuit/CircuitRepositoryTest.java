package com.telemetrix.circuit;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.RepositoryTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@RepositoryTest
class CircuitRepositoryTest {

	@Autowired
	CircuitRepository circuits;

	@Autowired
	TestEntityManager em;

	@Test
	void findsACircuitByRefWithAllItsFields() {
		var monza = new Circuit("monza", "Autodromo Nazionale di Monza");
		monza.setLocality("Monza");
		monza.setCountry("Italy");
		monza.setLatitude(new BigDecimal("45.615600"));
		monza.setLongitude(new BigDecimal("9.281110"));
		monza.setOpenf1CircuitKey(39);
		circuits.save(monza);
		em.flush();
		em.clear();

		var found = circuits.findByRef("monza").orElseThrow();

		assertThat(found.getName()).isEqualTo("Autodromo Nazionale di Monza");
		assertThat(found.getLocality()).isEqualTo("Monza");
		assertThat(found.getCountry()).isEqualTo("Italy");
		assertThat(found.getLatitude()).isEqualByComparingTo("45.6156");
		assertThat(found.getLongitude()).isEqualByComparingTo("9.28111");
		assertThat(circuits.findByRef("spa")).isEmpty();
	}

	@Test
	void findsACircuitByItsOpenF1Key() {
		var monza = new Circuit("monza", "Autodromo Nazionale di Monza");
		monza.setOpenf1CircuitKey(39);
		circuits.save(monza);

		assertThat(circuits.findByOpenf1CircuitKey(39)).map(Circuit::getRef).hasValue("monza");
		assertThat(circuits.findByOpenf1CircuitKey(7)).isEmpty();
	}

}
