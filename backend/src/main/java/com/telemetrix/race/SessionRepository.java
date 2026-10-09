package com.telemetrix.race;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<Session, Long> {

	Optional<Session> findByOpenf1SessionKey(Integer sessionKey);

}
