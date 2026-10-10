package com.telemetrix.ingestion;

import com.telemetrix.common.NotFoundException;
import com.telemetrix.ingestion.openf1.OpenF1Client;
import com.telemetrix.race.Race;
import com.telemetrix.race.RaceRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.stereotype.Service;

// "A user opened a race" (docs/adr/0003): if its data isn't downloaded yet, download it in the
// background — once, however many users open it at the same time — and report progress.
//
// Progress of running and failed imports lives in memory: a restart forgets them, and the next open
// simply starts again. A race that has happened is marked imported in the database (RaceImporter);
// one still to come is refreshed at most every `refreshAfter`.
@Service
public class RaceImportCoordinator {

	public enum State {
		/** Has OpenF1 data, but nobody has opened it yet. */
		NOT_IMPORTED,
		/** Downloading in the background. */
		IMPORTING,
		/** Downloaded: the race pages can be shown. */
		READY,
		/** The last download failed; opening the race again retries. */
		FAILED,
		/** Before OpenF1 (2023): nothing to download; results and history still work. */
		NOT_AVAILABLE
	}

	public record ImportStatus(long raceId, State state, Instant since, String message) {
	}

	private record Attempt(State state, Instant at, String message) {
	}

	private static final Logger log = LoggerFactory.getLogger(RaceImportCoordinator.class);

	private final RaceImporter importer;

	private final RaceRepository races;

	private final Clock clock;

	private final Executor background;

	private final Duration refreshAfter;

	private final ConcurrentHashMap<Long, Attempt> attempts = new ConcurrentHashMap<>();

	@Autowired
	public RaceImportCoordinator(RaceImporter importer, RaceRepository races, Clock clock,
			@Value("${telemetrix.import.race-refresh-after:15m}") Duration refreshAfter) {
		this(importer, races, clock, new SimpleAsyncTaskExecutor("race-import-"), refreshAfter);
	}

	RaceImportCoordinator(RaceImporter importer, RaceRepository races, Clock clock, Executor background,
			Duration refreshAfter) {
		this.importer = importer;
		this.races = races;
		this.clock = clock;
		this.background = background;
		this.refreshAfter = refreshAfter;
	}

	public ImportStatus status(long raceId) {
		Race race = races.findById(raceId).orElseThrow(() -> new NotFoundException("Race", raceId));
		if (race.getImportedAt() != null) {
			return new ImportStatus(raceId, State.READY, race.getImportedAt(), null);
		}
		if (race.getOpenf1MeetingKey() == null) {
			return new ImportStatus(raceId, State.NOT_AVAILABLE, null,
					"No OpenF1 data: it starts in " + OpenF1Client.FIRST_SEASON);
		}
		Attempt attempt = attempts.get(raceId);
		if (attempt == null) {
			return new ImportStatus(raceId, State.NOT_IMPORTED, null, null);
		}
		return new ImportStatus(raceId, attempt.state(), attempt.at(), attempt.message());
	}

	/** Called when a race page is opened: starts the download if it's needed. */
	public ImportStatus open(long raceId) {
		Race race = races.findById(raceId).orElseThrow(() -> new NotFoundException("Race", raceId));
		if (race.getOpenf1MeetingKey() == null) {
			return status(raceId); // nothing to download
		}
		if (race.getImportedAt() != null) {
			// downloaded; by an older importer, it's downloaded again in the background, while its pages
			// work with what's there (when the import ends, open pages hear and reload: TimelineUpdates)
			if (race.getImportVersion() < RaceImporter.IMPORT_VERSION) {
				startDownload(raceId);
			}
			return status(raceId);
		}
		Attempt attempt = startDownload(raceId);
		return new ImportStatus(raceId, attempt.state(), attempt.at(), attempt.message());
	}

	// Single flight: compute() is atomic per race, so exactly one caller decides to start the download.
	private Attempt startDownload(long raceId) {
		boolean[] started = { false };
		Attempt attempt = attempts.compute(raceId, (id, existing) -> {
			if (existing != null && (existing.state() == State.IMPORTING || isFresh(existing))) {
				return existing;
			}
			started[0] = true;
			return new Attempt(State.IMPORTING, clock.instant(), null);
		});
		if (started[0]) {
			background.execute(() -> download(raceId));
		}
		return attempt;
	}

	// A race still to come was downloaded recently: don't fetch it again on every open.
	private boolean isFresh(Attempt attempt) {
		return attempt.state() == State.READY && !attempt.at().plus(refreshAfter).isBefore(clock.instant());
	}

	private void download(long raceId) {
		try {
			importer.importRace(raceId);
			attempts.put(raceId, new Attempt(State.READY, clock.instant(), null));
		}
		catch (RuntimeException e) {
			log.warn("Importing race {} failed: {}", raceId, e.getMessage());
			attempts.put(raceId, new Attempt(State.FAILED, clock.instant(), e.getMessage()));
		}
	}

}
