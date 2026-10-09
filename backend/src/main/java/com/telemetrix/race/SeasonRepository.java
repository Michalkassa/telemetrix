package com.telemetrix.race;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeasonRepository extends JpaRepository<Season, Short> {

	List<Season> findAllByOrderByYearDesc();

}
