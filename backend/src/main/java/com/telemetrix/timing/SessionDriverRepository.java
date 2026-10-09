package com.telemetrix.timing;

import com.telemetrix.race.Session;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionDriverRepository extends JpaRepository<SessionDriver, SessionDriver.Key> {

	@Query("select d from SessionDriver d where d.session = :session order by d.id.driverNumber")
	List<SessionDriver> findBySessionOrderByDriverNumberAsc(@Param("session") Session session);

	@Modifying
	@Query("delete from SessionDriver d where d.session = :session")
	int deleteBySession(@Param("session") Session session);

}
