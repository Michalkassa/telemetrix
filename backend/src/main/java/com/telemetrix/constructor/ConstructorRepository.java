package com.telemetrix.constructor;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConstructorRepository extends JpaRepository<Constructor, Long> {

	Optional<Constructor> findByRef(String ref);

}
