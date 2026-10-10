package com.telemetrix.event;

import com.telemetrix.ingestion.RaceImported;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

// Pushes timeline updates over the WebSocket (docs/adr/0005): when a race's data is imported again (a
// race still being run, or a re-import), /topic/races/{id}/timeline tells everyone with it open, and they
// fetch the timeline again. Sent after the import has committed and after the cached timelines and race
// states were dropped (they listen first), so the fetch gets the new data.
@Component
class TimelineUpdates {

	/** The message: this race's timeline changed (`change`: TIMELINE). */
	record TimelineChanged(long raceId, String change) {
	}

	private final SimpMessageSendingOperations broker;

	TimelineUpdates(SimpMessageSendingOperations broker) {
		this.broker = broker;
	}

	@TransactionalEventListener(fallbackExecution = true)
	@Order(10)
	void onRaceImported(RaceImported imported) {
		broker.convertAndSend("/topic/races/" + imported.raceId() + "/timeline",
				new TimelineChanged(imported.raceId(), "TIMELINE"));
	}

}
