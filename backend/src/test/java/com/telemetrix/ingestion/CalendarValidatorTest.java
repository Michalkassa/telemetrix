package com.telemetrix.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.ingestion.jolpica.JolpicaRace;
import com.telemetrix.ingestion.openf1.OpenF1Session;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class CalendarValidatorTest {

	static final JolpicaRace.Circuit MONZA = new JolpicaRace.Circuit("monza", "Autodromo Nazionale di Monza",
			new JolpicaRace.Location(new BigDecimal("45.6156"), new BigDecimal("9.28111"), "Monza", "Italy"));

	static final LocalDate RACE_DAY = LocalDate.parse("2024-09-01");

	CalendarValidator validator = new CalendarValidator();

	@Test
	void acceptsACompleteRace() {
		assertThat(validator.checkRace(new JolpicaRace(2024, 16, "Italian Grand Prix", MONZA, RACE_DAY))).isEmpty();
	}

	@Test
	void rejectsARaceWithoutADate() {
		assertThat(validator.checkRace(new JolpicaRace(2024, 16, "Italian Grand Prix", MONZA, null)))
				.hasValueSatisfying(reason -> assertThat(reason).contains("Italian Grand Prix").contains("no date"));
	}

	@Test
	void rejectsARaceWithoutAValidRound() {
		assertThat(validator.checkRace(new JolpicaRace(2024, 0, "Italian Grand Prix", MONZA, RACE_DAY)))
				.hasValueSatisfying(reason -> assertThat(reason).contains("round"));
	}

	@Test
	void rejectsARaceWithoutANameOrCircuit() {
		assertThat(validator.checkRace(new JolpicaRace(2024, 16, " ", MONZA, RACE_DAY))).isPresent();
		assertThat(validator.checkRace(new JolpicaRace(2024, 16, "Italian Grand Prix", null, RACE_DAY)))
				.hasValueSatisfying(reason -> assertThat(reason).contains("circuit"));
	}

	@Test
	void acceptsASessionOfTheRaceWeekend() {
		assertThat(validator.checkSession(session("Practice 1", "2024-08-30T11:30:00Z", "2024-08-30T12:30:00Z"),
				RACE_DAY)).isEmpty();
	}

	@Test
	void rejectsASessionThatEndsBeforeItStarts() {
		assertThat(validator.checkSession(session("Race", "2024-09-01T13:00:00Z", "2024-09-01T12:00:00Z"), RACE_DAY))
				.hasValueSatisfying(reason -> assertThat(reason).contains("Race").contains("ends before it starts"));
	}

	@Test
	void rejectsASessionOutsideTheRaceWeekend() {
		assertThat(validator.checkSession(session("Race", "2024-09-08T13:00:00Z", "2024-09-08T15:00:00Z"), RACE_DAY))
				.hasValueSatisfying(reason -> assertThat(reason).contains("outside the race weekend"));
	}

	@Test
	void rejectsASessionWithoutAStartTime() {
		assertThat(validator.checkSession(new OpenF1Session(1, 1, "Race", "Race", 39, null, null, 2024, false),
				RACE_DAY)).hasValueSatisfying(reason -> assertThat(reason).contains("no start time"));
	}

	private static OpenF1Session session(String name, String start, String end) {
		return new OpenF1Session(1, 1, name, name, 39, OffsetDateTime.parse(start), OffsetDateTime.parse(end), 2024,
				false);
	}

}
