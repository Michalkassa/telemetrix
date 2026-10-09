package com.telemetrix.driver;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverRepository extends JpaRepository<Driver, Long> {

	Optional<Driver> findByRef(String ref);

}
