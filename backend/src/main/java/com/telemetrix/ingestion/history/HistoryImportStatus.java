package com.telemetrix.ingestion.history;

import java.time.Instant;

// A history import run, as the admin endpoint shows it. executionId is null until the job starts
// (the dump is downloaded first).
public record HistoryImportStatus(Long executionId, String status, Instant startedAt, Instant endedAt, String exitCode) {
}
