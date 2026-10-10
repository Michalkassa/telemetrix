package com.telemetrix.ingestion.history;

/** Published when the history import has completed: anything worked out from the history is out of date. */
public record HistoryImported(long executionId) {
}
