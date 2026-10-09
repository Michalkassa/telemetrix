package com.telemetrix.race;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.RepositoryTest;
import com.telemetrix.circuit.Circuit;
import com.telemetrix.circuit.CircuitRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RepositoryTest
class SeasonRepositoryTest {

	@Autowired
	SeasonRepository seasons;

	@Autowired
	RaceRepository races;

	@Autowired
	CircuitRepository circuits;

	@Test
	void listsSeasonsNewestFirst() {
		seasons.save(new Season((short) 1950));
		seasons.save(new Season((short) 2024));
		seasons.save(new Season((short) 2008));

		assertThat(seasons.findAllByOrderByYearDesc()).extracting(Season::getYear)
				.containsExactly((short) 2024, (short) 2008, (short) 1950);
	}


	@Test
	void countsEachSeasonsRacesNewestFirstIncludingEmptySeasons() {
		var monza = circuits.save(new Circuit("monza", "Monza"));
		var s2024 = seasons.save(new Season((short) 2024));
		seasons.save(new Season((short) 2025));
		var s1950 = seasons.save(new Season((short) 1950));
		races.save(new Race(s2024, (short) 1, monza, "A", LocalDate.parse("2024-03-02")));
		races.save(new Race(s2024, (short) 2, monza, "B", LocalDate.parse("2024-03-09")));
		races.save(new Race(s1950, (short) 1, monza, "C", LocalDate.parse("1950-05-13")));

		assertThat(seasons.findAllWithRaceCounts()).containsExactly(
				new SeasonRepository.SeasonRaceCount((short) 2025, 0L),
				new SeasonRepository.SeasonRaceCount((short) 2024, 2L),
				new SeasonRepository.SeasonRaceCount((short) 1950, 1L));
	}

}
