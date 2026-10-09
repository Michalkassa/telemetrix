package com.telemetrix.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.telemetrix.FakeTime;
import com.telemetrix.circuit.Circuit;
import com.telemetrix.common.NotFoundException;
import com.telemetrix.ingestion.RaceImportCoordinator.State;
import com.telemetrix.race.Race;
import com.telemetrix.race.RaceRepository;
import com.telemetrix.race.Season;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// "Open a race": import it in the background once, report progress, retry after a failure.
// The executor is cranked by hand, so the tests decide when the background work runs.
class RaceImportCoordinatorTest {

	static final long ID = 7;

	RaceImporter importer = mock(RaceImporter.class);

	RaceRepository races = mock(RaceRepository.class);

	FakeTime time = new FakeTime(Instant.parse("2026-10-09T12:00:00Z"));

	List<Runnable> background = new CopyOnWriteArrayList<>();

	RaceImportCoordinator coordinator = new RaceImportCoordinator(importer, races, time, background::add,
			Duration.ofMinutes(15));

	Race race;

	@BeforeEach
	void aRaceWithOpenF1Data() {
		race = new Race(new Season((short) 2024), (short) 1, new Circuit("bahrain", "Bahrain"), "Bahrain Grand Prix",
				LocalDate.parse("2024-03-02"));
		race.setOpenf1MeetingKey(1229);
		given(races.findById(ID)).willReturn(Optional.of(race));
		given(importer.importRace(ID)).willReturn(new ImportReport(Map.of(), List.of()));
	}

	@Test
	void anImportedRaceIsReady() {
		race.markImported(Instant.parse("2026-10-01T10:00:00Z"));

		assertThat(coordinator.open(ID).state()).isEqualTo(State.READY);
		assertThat(background).isEmpty();
	}

	@Test
	void aRaceBeforeOpenF1HasNothingToImport() {
		race.setOpenf1MeetingKey(null);

		var status = coordinator.open(ID);

		assertThat(status.state()).isEqualTo(State.NOT_AVAILABLE);
		assertThat(status.message()).contains("2023");
		assertThat(background).isEmpty();
	}

	@Test
	void aRaceNobodyOpenedIsNotImported() {
		assertThat(coordinator.status(ID).state()).isEqualTo(State.NOT_IMPORTED);
		verify(importer, never()).importRace(ID);
	}

	@Test
	void openingStartsTheImportInTheBackgroundThenItIsReady() {
		var opened = coordinator.open(ID);

		assertThat(opened.state()).isEqualTo(State.IMPORTING);
		assertThat(opened.since()).isEqualTo(time.instant());
		verify(importer, never()).importRace(ID); // not yet: it runs in the background

		runBackgroundWork();

		verify(importer).importRace(ID);
		assertThat(coordinator.status(ID).state()).isEqualTo(State.READY);
	}

	@Test
	void openingAgainWhileImportingDoesNotStartASecondImport() {
		coordinator.open(ID);
		assertThat(coordinator.open(ID).state()).isEqualTo(State.IMPORTING);

		assertThat(background).hasSize(1);
	}

	@Test
	void manyUsersOpeningAtOnceTriggerOneImport() throws Exception {
		var pool = Executors.newFixedThreadPool(8);
		var start = new CountDownLatch(1);
		var done = new CountDownLatch(8);
		for (int i = 0; i < 8; i++) {
			pool.execute(() -> {
				try {
					start.await();
					coordinator.open(ID);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				finally {
					done.countDown();
				}
			});
		}
		start.countDown();
		assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
		pool.shutdown();

		assertThat(background).hasSize(1);
	}

	@Test
	void aFailedImportSaysWhyAndOpeningAgainRetries() {
		given(importer.importRace(ID)).willThrow(new ExternalApiException("OpenF1 GET /laps?session_key=9472 failed", null))
				.willReturn(new ImportReport(Map.of(), List.of()));
		coordinator.open(ID);
		runBackgroundWork();

		var failed = coordinator.status(ID);
		assertThat(failed.state()).isEqualTo(State.FAILED);
		assertThat(failed.message()).contains("OpenF1 GET /laps");

		assertThat(coordinator.open(ID).state()).isEqualTo(State.IMPORTING);
		runBackgroundWork();
		assertThat(coordinator.status(ID).state()).isEqualTo(State.READY);
		verify(importer, times(2)).importRace(ID);
	}

	@Test
	void aRaceStillToComeIsRefreshedOnlyAfterTheRefreshInterval() {
		// imported, but not marked done (it hasn't happened yet): kept fresh, not re-fetched on every open
		coordinator.open(ID);
		runBackgroundWork();

		time.advance(Duration.ofMinutes(10));
		assertThat(coordinator.open(ID).state()).isEqualTo(State.READY);
		assertThat(background).isEmpty();

		time.advance(Duration.ofMinutes(6));
		assertThat(coordinator.open(ID).state()).isEqualTo(State.IMPORTING);
		assertThat(background).hasSize(1);
	}

	@Test
	void anUnknownRaceIsNotFound() {
		given(races.findById(99L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> coordinator.open(99)).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> coordinator.status(99)).isInstanceOf(NotFoundException.class);
	}

	private void runBackgroundWork() {
		var tasks = new ArrayList<>(background);
		background.clear();
		tasks.forEach(Runnable::run);
	}

}
