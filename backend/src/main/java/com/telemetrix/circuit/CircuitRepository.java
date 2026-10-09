package com.telemetrix.circuit;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CircuitRepository extends JpaRepository<Circuit, Long> {

	Optional<Circuit> findByRef(String ref);

	Optional<Circuit> findByOpenf1CircuitKey(Integer circuitKey);

}
