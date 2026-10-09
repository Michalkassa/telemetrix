package com.telemetrix.ingestion;

import java.util.List;

/** Published after a race's data was imported: anything worked out from its sessions is out of date. */
public record RaceImported(long raceId, List<Long> sessionIds) {
}
