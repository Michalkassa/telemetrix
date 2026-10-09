package com.telemetrix.race;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.RepositoryTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RepositoryTest
class SeasonRepositoryTest {

	@Autowired
	SeasonRepository seasons;

	@Test
	void listsSeasonsNewestFirst() {
		seasons.save(new Season((short) 1950));
		seasons.save(new Season((short) 2024));
		seasons.save(new Season((short) 2008));

		assertThat(seasons.findAllByOrderByYearDesc()).extracting(Season::getYear)
				.containsExactly((short) 2024, (short) 2008, (short) 1950);
	}

}
