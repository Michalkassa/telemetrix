package com.telemetrix.timing;

import com.telemetrix.race.Session;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TireStintRepository extends JpaRepository<TireStint, Long> {

	List<TireStint> findBySessionOrderByDriverNumberAsc(Session session);

	long countBySession(Session session);

	// A session's timing is replaced as a whole when it is imported again.
	@Modifying
	@Query("delete from TireStint x where x.session = :session")
	int deleteBySession(@Param("session") Session session);

}
