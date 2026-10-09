package com.telemetrix.race;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SeasonRepository extends JpaRepository<Season, Short> {

	List<Season> findAllByOrderByYearDesc();

	// Every season (also those without races yet) with its number of races, newest first.
	@Query("""
			select new com.telemetrix.race.SeasonRepository$SeasonRaceCount(s.year, count(r))
			from Season s left join Race r on r.season = s
			group by s.year
			order by s.year desc""")
	List<SeasonRaceCount> findAllWithRaceCounts();

	record SeasonRaceCount(Short year, Long raceCount) {
	}

}
