package com.telemetrix.race;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RaceRepository extends JpaRepository<Race, Long> {

	// Sessions are loaded in the same query, so walking race.getSessions() costs no extra SQL (no N+1).
	@EntityGraph(attributePaths = "sessions")
	List<Race> findBySeasonYearOrderByRaceDate(short year);

	Optional<Race> findBySeasonYearAndRound(short year, short round);

	Optional<Race> findByOpenf1MeetingKey(Integer meetingKey);

}
