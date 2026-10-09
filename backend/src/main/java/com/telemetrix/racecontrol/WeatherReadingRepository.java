package com.telemetrix.racecontrol;

import com.telemetrix.race.Session;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeatherReadingRepository extends JpaRepository<WeatherReading, Long> {

	List<WeatherReading> findBySessionOrderByRecordedAtAsc(Session session);

	// replaced as a whole when the session is imported again
	@Modifying
	@Query("delete from WeatherReading x where x.session = :session")
	int deleteBySession(@Param("session") Session session);

}
