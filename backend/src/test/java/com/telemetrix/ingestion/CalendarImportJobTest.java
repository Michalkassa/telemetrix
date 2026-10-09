package com.telemetrix.ingestion;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CalendarImportJobTest {

	@Test
	void importsTheCurrentSeasonByTheClock() {
		var importer = mock(CalendarImporter.class);
		var job = new CalendarImportJob(importer, Clock.fixed(Instant.parse("2026-10-09T04:00:00Z"), ZoneOffset.UTC));

		job.importCurrentSeason();

		verify(importer).importSeason(2026);
	}

}
