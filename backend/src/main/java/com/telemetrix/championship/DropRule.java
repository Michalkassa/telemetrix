package com.telemetrix.championship;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

// Until 1990 only a driver's best results counted towards the championship; some seasons counted the
// best of each half separately. Taken from the data: for each season 1950-1990 these are the rules that
// make every driver's total equal the official one (DropRulesTest checks them all against the Jolpica
// API's standings). Seasons not listed counted every result.
public sealed interface DropRule {

	/** The points that count, from a driver's Grand Prix results (round, points). */
	double total(List<RoundPoints> results);

	record RoundPoints(int round, double points) {
	}

	record All() implements DropRule {

		@Override
		public double total(List<RoundPoints> results) {
			return results.stream().mapToDouble(RoundPoints::points).sum();
		}

	}

	/** The best `count` results. */
	record Best(int count) implements DropRule {

		@Override
		public double total(List<RoundPoints> results) {
			return best(results, count);
		}

	}

	/** The best `first` of rounds 1..`lastOfFirstHalf`, plus the best `second` of the rest. */
	record Halves(int lastOfFirstHalf, int first, int second) implements DropRule {

		@Override
		public double total(List<RoundPoints> results) {
			return best(results.stream().filter(r -> r.round() <= lastOfFirstHalf).toList(), first)
					+ best(results.stream().filter(r -> r.round() > lastOfFirstHalf).toList(), second);
		}

	}

	private static double best(List<RoundPoints> results, int count) {
		return results.stream().map(RoundPoints::points).sorted(Comparator.reverseOrder()).limit(count)
				.mapToDouble(Double::doubleValue).sum();
	}

	Map<Integer, DropRule> BY_SEASON = Map.ofEntries(
			Map.entry(1950, new Best(4)), Map.entry(1951, new Best(4)), Map.entry(1952, new Best(4)),
			Map.entry(1953, new Best(4)), Map.entry(1954, new Best(5)), Map.entry(1955, new Best(5)),
			Map.entry(1956, new Best(5)), Map.entry(1957, new Best(5)), Map.entry(1958, new Best(6)),
			Map.entry(1959, new Best(5)), Map.entry(1960, new Best(6)), Map.entry(1961, new Best(5)),
			Map.entry(1962, new Best(5)), Map.entry(1963, new Best(6)), Map.entry(1964, new Best(6)),
			Map.entry(1965, new Best(6)), Map.entry(1966, new Best(5)), Map.entry(1977, new Halves(7, 5, 6)),
			Map.entry(1979, new Halves(7, 4, 4)), Map.entry(1980, new Halves(7, 5, 5)), Map.entry(1985, new Best(11)),
			Map.entry(1986, new Best(11)), Map.entry(1987, new Best(11)), Map.entry(1988, new Best(11)),
			Map.entry(1989, new Best(11)), Map.entry(1990, new Best(11)));

	static DropRule forSeason(int season) {
		return BY_SEASON.getOrDefault(season, new All());
	}

}
