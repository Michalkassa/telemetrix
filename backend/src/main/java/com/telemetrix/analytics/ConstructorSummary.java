package com.telemetrix.analytics;

import java.util.List;

/** A constructor in the directory: every season it raced in, its starts and wins. */
public record ConstructorSummary(String ref, String name, String nationality, List<Integer> seasons, int starts,
		int wins) {
}
