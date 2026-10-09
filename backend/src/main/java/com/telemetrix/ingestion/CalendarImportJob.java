package com.telemetrix.ingestion;

import java.time.Clock;
import java.time.Year;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Keeps the current season's calendar fresh (rounds added or moved, OpenF1 meetings appearing).
@Component
class CalendarImportJob {

	private static final Logger log = LoggerFactory.getLogger(CalendarImportJob.class);

	private final CalendarImporter importer;

	private final Clock clock;

	CalendarImportJob(CalendarImporter importer, Clock clock) {
		this.importer = importer;
		this.clock = clock;
	}

	@Scheduled(cron = "${telemetrix.import.calendar-cron:0 0 4 * * *}", zone = "UTC")
	void importCurrentSeason() {
		int year = Year.now(clock).getValue();
		log.info("Nightly calendar import for {}", year);
		importer.importSeason(year);
	}

}
