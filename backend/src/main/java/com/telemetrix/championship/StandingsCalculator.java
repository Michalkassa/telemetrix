package com.telemetrix.championship;

import com.telemetrix.championship.DropRule.RoundPoints;
import com.telemetrix.championship.ResultLine.Kind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// Standings from a season's results with the points awarded (the official ones: they already include
// fastest-lap points, shared drives, half and double points). Drivers' totals count only what the
// season's DropRule allows; level drivers are split by count-back: most wins, then most second places,
// and so on — Grand Prix results only, not sprints (OfficialStandingsTest checks this against the
// official tables). Constructors add up every point their cars scored.
public final class StandingsCalculator {

	public record Standings(int season, List<Standing> drivers, List<Standing> constructors) {
	}

	private StandingsCalculator() {
	}

	public static Standings compute(int season, List<ResultLine> results) {
		DropRule rule = DropRule.forSeason(season);
		var drivers = rank(results, ResultLine::driver, lines -> {
			double sprints = lines.stream().filter(l -> l.kind() == Kind.SPRINT).mapToDouble(ResultLine::points).sum();
			return rule.total(lines.stream().filter(l -> l.kind() == Kind.GRAND_PRIX)
					.map(l -> new RoundPoints(l.round(), l.points())).toList()) + sprints;
		});
		var constructors = rank(results, ResultLine::constructor,
				lines -> lines.stream().mapToDouble(ResultLine::points).sum());
		return new Standings(season, drivers, constructors);
	}

	private static List<Standing> rank(List<ResultLine> results, Function<ResultLine, String> id,
			Function<List<ResultLine>, Double> total) {
		Map<String, List<ResultLine>> byId = results.stream()
				.collect(Collectors.groupingBy(id, LinkedHashMap::new, Collectors.toList()));
		record Entry(String id, double points, int[] places) {
		}
		List<Entry> entries = new ArrayList<>();
		byId.forEach((key, lines) -> {
			int[] places = new int[64];
			lines.stream().filter(l -> l.kind() == Kind.GRAND_PRIX && l.position() != null && l.position() < places.length)
					.forEach(l -> places[l.position()]++);
			entries.add(new Entry(key, Math.round(total.apply(lines) * 100) / 100.0, places));
		});
		Comparator<Entry> order = Comparator.comparingDouble((Entry e) -> -e.points());
		for (int p = 1; p < 64; p++) {
			int place = p;
			order = order.thenComparingInt(e -> -e.places()[place]);
		}
		order = order.thenComparing(Entry::id);
		entries.sort(order);
		List<Standing> table = new ArrayList<>();
		for (int i = 0; i < entries.size(); i++) {
			Entry e = entries.get(i);
			table.add(new Standing(i + 1, e.id(), e.points(), e.places()[1], tieKey(e.points(), e.places())));
		}
		return table;
	}

	// equal for entries the rules can't separate
	private static String tieKey(double points, int[] places) {
		var key = new StringBuilder().append(points);
		for (int p = 1; p < places.length; p++) {
			key.append(',').append(places[p]);
		}
		return key.toString();
	}

}
