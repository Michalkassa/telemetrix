package com.telemetrix.analytics;

import com.telemetrix.ingestion.RaceImported;
import com.telemetrix.ingestion.history.HistoryImported;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

// Keeps the analytics view up to date: after the history import, and after a race import (once it has
// committed, in a transaction of its own), which brings the race's weather.
@Component
class DriverStatsRefresher {

	private final DriverStatsService stats;

	DriverStatsRefresher(DriverStatsService stats) {
		this.stats = stats;
	}

	@EventListener
	void onHistoryImported(HistoryImported imported) {
		stats.refresh();
	}

	@TransactionalEventListener(fallbackExecution = true)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	void onRaceImported(RaceImported imported) {
		stats.refresh(imported.raceId());
	}

}
