package com.telemetrix.championship;

import com.telemetrix.common.NotFoundException;
import com.telemetrix.event.RaceData;
import com.telemetrix.event.RaceDataLoader;
import com.telemetrix.state.CarStatus;
import com.telemetrix.state.RaceStateService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

// The championship as it stands during a replay: the standings before the race, plus — for each car in
// the running order at that moment (retired cars score nothing) — the points that place would earn
// under the season's PointsSystem, with the fastest lap so far.
@Service
public class LiveStandingsService {

	private final JdbcTemplate jdbc;

	private final ChampionshipService championship;

	private final RaceStateService states;

	private final RaceDataLoader races;

	LiveStandingsService(JdbcTemplate jdbc, ChampionshipService championship, RaceStateService states,
			RaceDataLoader races) {
		this.jdbc = jdbc;
		this.championship = championship;
		this.states = states;
		this.races = races;
	}

	record Entry(String ref, String code, String surname) {
	}

	/** What a race's live standings need: who drives which car, the points before the race, the rules. */
	record Context(int season, int round, boolean sprint, Map<Integer, Entry> cars, Map<String, Double> before,
			PointsSystem rules) {
	}

	/** A car's place right now: null when it isn't classified (retired, or not started). */
	record Placed(int number, Integer position) {
	}

	public LiveStandings at(long sessionId, Instant at) {
		Context context = context(sessionId)
				.orElseThrow(() -> new NotFoundException("Race history for session", sessionId));
		List<Placed> order = states.stateAt(sessionId, at).cars().stream()
				.map(car -> new Placed(car.number(),
						car.status() == CarStatus.RETIRED || car.status() == CarStatus.ON_GRID ? null : car.position()))
				.toList();
		return new LiveStandings(context.season(), context.round(), at,
				table(context, order, fastestLapSoFar(races.load(sessionId), at)));
	}

	/** The race's context; empty for an unknown session, or a race the history doesn't have yet. */
	Optional<Context> context(long sessionId) {
		var race = jdbc.query("""
				select r.id, r.season_year, r.round, s.name from sessions s join races r on r.id = s.race_id
				where s.id = ?""", (rs, i) -> new Object[] { rs.getLong("id"), rs.getInt("season_year"),
				rs.getInt("round"), rs.getString("name") }, sessionId).stream().findFirst();
		if (race.isEmpty()) {
			return Optional.empty();
		}
		long raceId = (Long) race.get()[0];
		int season = (Integer) race.get()[1];
		int round = (Integer) race.get()[2];
		Map<Integer, Entry> cars = new HashMap<>();
		jdbc.query("""
				select e.car_number, d.ref, d.code, d.family_name from race_entries e join drivers d on d.id = e.driver_id
				where e.race_id = ? and e.car_number is not null""", rs -> {
			cars.putIfAbsent(rs.getInt("car_number"), new Entry(rs.getString("ref"),
					rs.getString("code") == null ? null : rs.getString("code").trim(), rs.getString("family_name")));
		}, raceId);
		if (cars.isEmpty()) {
			return Optional.empty(); // the history isn't imported: no one to score
		}
		Map<String, Double> before = new HashMap<>();
		if (round > 1) {
			championship.standings(season, round - 1).drivers().forEach(d -> before.put(d.ref(), d.points()));
		}
		boolean sprint = "Sprint".equals(race.get()[3]);
		if (!sprint) {
			// the same round's sprint is run before its Grand Prix: those points are already scored
			jdbc.query("""
					select d.ref, sr.points from session_results sr join race_entries e on e.id = sr.race_entry_id
					join drivers d on d.id = e.driver_id
					where e.race_id = ? and sr.session_type = 'SR' and sr.points is not null""",
					rs -> {
						before.merge(rs.getString("ref"), rs.getBigDecimal("points").doubleValue(), Double::sum);
					}, raceId);
		}
		return Optional.of(new Context(season, round, sprint, cars, before, PointsSystem.forSeason(season)));
	}

	/** The drivers' table if the race finished with this order. */
	static List<LiveStandings.Driver> table(Context context, List<Placed> order, Integer fastestCar) {
		Map<String, Integer> positionBefore = new HashMap<>();
		var earlier = context.before().entrySet().stream()
				.sorted(Map.Entry.<String, Double>comparingByValue().reversed()).toList();
		for (int i = 0; i < earlier.size(); i++) {
			positionBefore.put(earlier.get(i).getKey(), i + 1);
		}
		List<LiveStandings.Driver> drivers = new ArrayList<>();
		for (Placed car : order) {
			Entry entry = context.cars().get(car.number());
			if (entry == null) {
				continue;
			}
			double points = context.sprint() ? context.rules().sprint(car.position())
					: context.rules().race(car.position(), fastestCar != null && fastestCar == car.number(),
							PointsSystem.Scale.FULL);
			double before = context.before().getOrDefault(entry.ref(), 0.0);
			drivers.add(new LiveStandings.Driver(0, positionBefore.get(entry.ref()), car.number(), entry.ref(),
					entry.code(), entry.surname(), before, points, before + points));
		}
		drivers.sort(Comparator.comparingDouble(LiveStandings.Driver::total).reversed()
				.thenComparing(LiveStandings.Driver::positionBefore, Comparator.nullsLast(Comparator.naturalOrder())));
		List<LiveStandings.Driver> ranked = new ArrayList<>();
		for (int i = 0; i < drivers.size(); i++) {
			var d = drivers.get(i);
			ranked.add(new LiveStandings.Driver(i + 1, d.positionBefore(), d.number(), d.ref(), d.code(), d.surname(),
					d.before(), d.race(), d.total()));
		}
		return ranked;
	}

	/** The car with the fastest lap completed by `at`. */
	static Integer fastestLapSoFar(RaceData race, Instant at) {
		return race.laps().stream()
				.filter(l -> l.lapMs() != null && l.startsAt() != null && !l.startsAt().plusMillis(l.lapMs()).isAfter(at))
				.min(Comparator.comparing(RaceData.LapTime::lapMs))
				.map(RaceData.LapTime::car)
				.orElse(null);
	}

}
