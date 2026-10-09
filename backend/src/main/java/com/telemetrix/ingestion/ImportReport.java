package com.telemetrix.ingestion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// What an import did, per kind of row ("races", "sessions", ...), plus human-readable notes
// (skipped rows and why, "already imported", ...). Returned by the admin endpoints as JSON.
public record ImportReport(Map<String, Counts> counts, List<String> notes) {

	public record Counts(int inserted, int updated, int unchanged, int skipped) {
	}

	public Counts count(String kind) {
		return counts.getOrDefault(kind, new Counts(0, 0, 0, 0));
	}

	static Builder builder() {
		return new Builder();
	}

	static final class Builder {

		private final Map<String, int[]> counts = new LinkedHashMap<>();

		private final List<String> notes = new ArrayList<>();

		Builder inserted(String kind) {
			counter(kind)[0]++;
			return this;
		}

		// updated if something changed, unchanged otherwise
		Builder changed(String kind, boolean changed) {
			counter(kind)[changed ? 1 : 2]++;
			return this;
		}

		Builder skipped(String kind, String reason) {
			counter(kind)[3]++;
			notes.add(reason);
			return this;
		}

		Builder note(String note) {
			notes.add(note);
			return this;
		}

		ImportReport build() {
			var result = new LinkedHashMap<String, Counts>();
			counts.forEach((kind, c) -> result.put(kind, new Counts(c[0], c[1], c[2], c[3])));
			return new ImportReport(result, List.copyOf(notes));
		}

		private int[] counter(String kind) {
			return counts.computeIfAbsent(kind, k -> new int[4]);
		}

	}

}
