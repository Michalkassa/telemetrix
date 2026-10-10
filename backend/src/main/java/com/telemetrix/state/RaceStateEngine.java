package com.telemetrix.state;

import com.telemetrix.event.RaceData;
import com.telemetrix.event.RaceData.LapTime;
import com.telemetrix.event.RaceData.Stint;
import com.telemetrix.racecontrol.PenaltyKind;
import com.telemetrix.racecontrol.RaceControlParser;
import com.telemetrix.racecontrol.RaceControlSignal;
import com.telemetrix.racecontrol.TrackStatus;
import com.telemetrix.racecontrol.TrackStatusPeriod;
import com.telemetrix.replay.ProgressTimeline;
import com.telemetrix.replay.RaceReplay.PitWindow;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// A race's state at any moment (RaceState). Positions, laps and gaps come straight from the timing
// (instant, docs/adr/0004); the rest — flag, tyres, pit stops, penalties — is folded from the race's
// events in time order. Snapshots of that folded part, taken every 5 s, every lap and at key events, mean
// a seek only replays the few events since the nearest snapshot: scrubbing stays instant anywhere.
public final class RaceStateEngine {

	static final Duration SNAPSHOT_INTERVAL = Duration.ofSeconds(5);

	// within this many seconds of the car ahead (at the detection point; here: anywhere) a car may use DRS
	static final double DRS_GAP = 1.0;

	// what the fold reads, in time order; `order` breaks ties at the same moment
	sealed interface Input {

		Instant at();

		int order();

	}

	record FlagChanged(Instant at, Flag flag, int order) implements Input {
	}

	record PitIn(Instant at, int car, int order) implements Input {
	}

	record PitOut(Instant at, int car, int order) implements Input {
	}

	record TyresFitted(Instant at, int car, String compound, int lapStart, int ageAtStart, int order)
			implements Input {
	}

	record PenaltyGiven(Instant at, int car, PenaltyKind kind, Integer amount, int order) implements Input {
	}

	/** `flag` null: the sector is clear. */
	record SectorFlagged(Instant at, int sector, SectorFlag flag, int order) implements Input {
	}

	record TrackCleared(Instant at, int order) implements Input {
	}

	record DrsChanged(Instant at, boolean enabled, int order) implements Input {
	}

	private final RaceData race;

	private final DerivedState initial;

	private final List<Input> inputs;

	private final List<Snapshot> snapshots;

	private final List<RaceData.Weather> weather;

	private RaceStateEngine(RaceData race) {
		this.race = race;
		this.weather = race.weather().stream().sorted(Comparator.comparing(RaceData.Weather::at)).toList();
		this.initial = initialState(race);
		this.inputs = inputs(race);
		this.snapshots = takeSnapshots();
	}

	public static RaceStateEngine of(RaceData race) {
		return new RaceStateEngine(race);
	}

	/** The state at a moment, from the nearest snapshot plus the events since. */
	public RaceState stateAt(Instant t) {
		Snapshot from = nearestSnapshot(t);
		return state(t, fold(from.state(), from.at(), t));
	}

	/** The same, folding every event from the start (for checking the snapshots). */
	public RaceState stateFromScratch(Instant t) {
		return state(t, fold(initial, Instant.MIN, t));
	}

	/** Every snapshot in time order, starting with the state before anything happened. */
	public List<Snapshot> snapshots() {
		return snapshots;
	}

	/** The latest snapshot at or before `t` (the starting state if there's none). */
	public Snapshot nearestSnapshot(Instant t) {
		int lo = 0;
		int hi = snapshots.size() - 1;
		while (lo < hi) {
			int mid = (lo + hi + 1) >>> 1;
			if (!snapshots.get(mid).at().isAfter(t)) {
				lo = mid;
			}
			else {
				hi = mid - 1;
			}
		}
		return snapshots.get(lo);
	}

	public Instant clockStart() {
		return race.clockStart();
	}

	// ---- the fold

	// applies the inputs after `after` up to and including `upTo`
	private DerivedState fold(DerivedState state, Instant after, Instant upTo) {
		int i = firstInputAfter(after);
		if (i >= inputs.size() || inputs.get(i).at().isAfter(upTo)) {
			return state;
		}
		var folding = new Folding(state);
		for (; i < inputs.size() && !inputs.get(i).at().isAfter(upTo); i++) {
			folding.apply(inputs.get(i));
		}
		return folding.freeze();
	}

	private int firstInputAfter(Instant after) {
		int lo = 0;
		int hi = inputs.size();
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (inputs.get(mid).at().isAfter(after)) {
				hi = mid;
			}
			else {
				lo = mid + 1;
			}
		}
		return lo;
	}

	private static final class Folding {

		Flag flag;

		final Map<Integer, SectorFlag> yellowSectors;

		boolean drsEnabled;

		final Map<Integer, DerivedState.Car> cars;

		Folding(DerivedState state) {
			this.flag = state.flag();
			this.yellowSectors = new HashMap<>(state.yellowSectors());
			this.drsEnabled = state.drsEnabled();
			this.cars = new HashMap<>(state.cars());
		}

		void apply(Input input) {
			switch (input) {
				case FlagChanged f -> flag = f.flag();
				// a drive-through or stop-go is served at the next stop
				case PitIn p -> update(p.car(), c -> c.withPit(c.pitStops() + 1, true, Math.max(0, c.pendingPenalties() - 1)));
				case PitOut p -> update(p.car(), c -> c.withPit(c.pitStops(), false, c.pendingPenalties()));
				case TyresFitted t -> update(t.car(), c -> c.withTyres(t.compound(), t.lapStart(), t.ageAtStart()));
				case PenaltyGiven p -> update(p.car(), c -> switch (p.kind()) {
					case TIME -> c.withPenalties(c.penaltySeconds() + (p.amount() == null ? 0 : p.amount()),
							c.pendingPenalties());
					case DRIVE_THROUGH, STOP_GO -> c.withPenalties(c.penaltySeconds(), c.pendingPenalties() + 1);
					case GRID -> c; // for the next race
				});
				case SectorFlagged f -> {
					if (f.flag() == null) {
						yellowSectors.remove(f.sector());
					}
					else {
						yellowSectors.put(f.sector(), f.flag());
					}
				}
				case TrackCleared c -> yellowSectors.clear();
				case DrsChanged d -> drsEnabled = d.enabled();
			}
		}

		private void update(int car, java.util.function.UnaryOperator<DerivedState.Car> change) {
			cars.put(car, change.apply(cars.getOrDefault(car, DerivedState.Car.NONE)));
		}

		DerivedState freeze() {
			return new DerivedState(flag, yellowSectors, drsEnabled, cars);
		}

	}

	// ---- the state at a moment: the folded part plus the timing

	private RaceState state(Instant t, DerivedState derived) {
		record Now(int car, ProgressTimeline timeline, double lapsDone, CarStatus status) {
		}
		List<Now> running = new ArrayList<>();
		List<Now> retired = new ArrayList<>();
		race.timelines().forEach((car, timeline) -> {
			double lapsDone = timeline.lapsDoneAt(t);
			Instant stopped = race.retiredAt(car);
			CarStatus status;
			if (stopped != null && !t.isBefore(stopped)) {
				status = CarStatus.RETIRED;
			}
			else if (timeline.isEmpty() || t.isBefore(timeline.firstAt())) {
				status = CarStatus.ON_GRID;
			}
			else if (!t.isBefore(timeline.lastAt())) {
				status = CarStatus.FINISHED;
			}
			else {
				status = derived.car(car).inPit() ? CarStatus.IN_PIT : CarStatus.RUNNING;
			}
			(status == CarStatus.RETIRED ? retired : running).add(new Now(car, timeline, lapsDone, status));
		});
		// furthest round first; level cars (finished, or stopped at the same point) by who got there first
		Comparator<Now> furthestFirst = Comparator.comparingDouble((Now n) -> -n.lapsDone())
				.thenComparing((Now n) -> n.timeline().reachedAt(n.lapsDone()), Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparingInt(Now::car);
		running.sort(furthestFirst);
		retired.sort(furthestFirst);

		List<CarState> cars = new ArrayList<>();
		Now leader = running.isEmpty() ? null : running.getFirst();
		Now ahead = null;
		for (Now now : running) {
			boolean timed = now.status() != CarStatus.ON_GRID;
			Instant at = now.timeline().lastAt() != null && now.timeline().lastAt().isBefore(t)
					? now.timeline().lastAt() : t; // a finished car's gaps freeze at the line
			Double gap = !timed ? null : now == leader ? 0.0 : behind(leader.timeline(), now.lapsDone(), at);
			Double interval = !timed || ahead == null ? null : behind(ahead.timeline(), now.lapsDone(), at);
			int lapsDown = leader == null ? 0 : (int) Math.floor(leader.lapsDone() - now.lapsDone());
			boolean drs = derived.drsEnabled() && interval != null && interval <= DRS_GAP && now.status() == CarStatus.RUNNING;
			cars.add(carState(now.car(), cars.size() + 1, now.lapsDone(), now.status(), gap, interval,
					Math.max(0, lapsDown), drs, derived));
			ahead = now;
		}
		for (Now now : retired) {
			cars.add(carState(now.car(), cars.size() + 1, now.lapsDone(), now.status(), null, null, 0, false, derived));
		}
		int lap = leader == null ? 1 : race.lapOf(leader.lapsDone());
		return new RaceState(t, lap, derived.flag(), derived.yellowSectors(), derived.drsEnabled(), weatherAt(t), cars);
	}

	private CarState carState(int car, int position, double lapsDone, CarStatus status, Double gap, Double interval,
			int lapsDown, boolean drs, DerivedState derived) {
		DerivedState.Car d = derived.car(car);
		int lap = race.lapOf(lapsDone);
		Integer tyreAge = d.stintLapStart() == null ? null : Math.max(0, lap - d.stintLapStart() + d.ageAtStart());
		return new CarState(car, race.code(car), position, lap, round(lapsDone, 10_000), status, gap, interval,
				lapsDown, d.compound(), tyreAge, d.pitStops(), d.penaltySeconds(), d.pendingPenalties(), drs,
				d.mandatoryStopDone());
	}

	// the latest weather reading at or before t
	private WeatherNow weatherAt(Instant t) {
		RaceData.Weather latest = null;
		for (RaceData.Weather w : weather) {
			if (w.at().isAfter(t)) {
				break;
			}
			latest = w;
		}
		return latest == null ? null : new WeatherNow(latest.airTemperature(), latest.trackTemperature(),
				latest.humidity(), latest.rainfall(), latest.windSpeed());
	}

	// seconds since `other` was where this car is now
	private static Double behind(ProgressTimeline other, double lapsDone, Instant at) {
		Instant otherWasHere = other.reachedAt(lapsDone);
		return otherWasHere == null ? null : Math.max(0, Duration.between(otherWasHere, at).toMillis()) / 1000.0;
	}

	private static double round(double value, int factor) {
		return Math.round(value * factor) / (double) factor;
	}

	// ---- building the inputs and snapshots

	// before anything happens: green, and each car on its first set of tyres
	private static DerivedState initialState(RaceData race) {
		Map<Integer, DerivedState.Car> cars = new HashMap<>();
		race.stints().stream()
				.filter(s -> s.stint() == firstStint(race, s.car()))
				.forEach(s -> cars.put(s.car(), DerivedState.Car.NONE.withTyres(s.compound(), s.lapStart(),
						s.ageAtStart())));
		return new DerivedState(Flag.GREEN, Map.of(), false, cars);
	}

	private static int firstStint(RaceData race, int car) {
		return race.stints().stream().filter(s -> s.car() == car).mapToInt(Stint::stint).min().orElse(1);
	}

	private static List<Input> inputs(RaceData race) {
		List<Input> inputs = new ArrayList<>();
		// at the same moment: a period ending, then the next starting; pit exit before entry; then the rest
		for (TrackStatusPeriod p : race.trackStatus()) {
			if (p.to() != null) {
				inputs.add(new FlagChanged(p.to(), Flag.GREEN, 0));
			}
		}
		for (TrackStatusPeriod p : race.trackStatus()) {
			inputs.add(new FlagChanged(p.from(), Flag.of(p.status()), 1));
		}
		race.pits().forEach((car, stops) -> stops.forEach(stop -> {
			inputs.add(new PitOut(stop.to(), car, 2));
			inputs.add(new PitIn(stop.from(), car, 3));
		}));
		for (Stint stint : race.stints()) {
			if (stint.stint() == firstStint(race, stint.car())) {
				continue;
			}
			Instant at = fittedAt(race, stint);
			if (at != null) {
				inputs.add(new TyresFitted(at, stint.car(), stint.compound(), stint.lapStart(), stint.ageAtStart(), 4));
			}
		}
		race.penalties().forEach(p -> inputs.add(new PenaltyGiven(p.at(), p.car(), p.kind(), p.amount(), 5)));
		for (RaceData.Message m : race.raceControl()) {
			String text = m.text() == null ? "" : m.text().trim();
			switch (RaceControlParser.parse(text)) {
				case RaceControlSignal.SectorFlag f -> inputs.add(new SectorFlagged(m.at(), f.sector(),
						!f.yellow() ? null : text.startsWith("DOUBLE") ? SectorFlag.DOUBLE_YELLOW : SectorFlag.YELLOW, 6));
				case RaceControlSignal.TrackClear c -> inputs.add(new TrackCleared(m.at(), 6));
				default -> {
					if (text.equals("DRS ENABLED") || text.equals("DRS DISABLED")) { // not one zone: DRS as a whole
						inputs.add(new DrsChanged(m.at(), text.equals("DRS ENABLED"), 6));
					}
				}
			}
		}
		// a stable sort: same moment and kind keep the order they came in
		inputs.sort(Comparator.comparing(Input::at).thenComparingInt(Input::order));
		return List.copyOf(inputs);
	}

	// at the pit stop on the lap before the stint (as the car leaves), else when the stint's first lap began
	private static Instant fittedAt(RaceData race, Stint stint) {
		return race.pits().getOrDefault(stint.car(), List.of()).stream()
				.filter(p -> Objects.equals(p.lap(), stint.lapStart() - 1))
				.map(PitWindow::to)
				.findFirst()
				.orElseGet(() -> race.laps().stream()
						.filter(l -> l.car() == stint.car() && l.lap() == stint.lapStart() && l.startsAt() != null)
						.map(LapTime::startsAt)
						.findFirst()
						.orElse(null));
	}

	private List<Snapshot> takeSnapshots() {
		record When(Instant at, SnapshotReason reason) {
		}
		List<When> when = new ArrayList<>();
		Instant end = race.timelines().values().stream().filter(t -> !t.isEmpty()).map(ProgressTimeline::lastAt)
				.max(Instant::compareTo).orElse(race.clockStart());
		for (Instant at = race.clockStart(); !at.isAfter(end.plus(SNAPSHOT_INTERVAL)); at = at.plus(SNAPSHOT_INTERVAL)) {
			when.add(new When(at, SnapshotReason.INTERVAL));
		}
		for (int k = 0; k < Math.max(race.totalLaps(), 0); k++) {
			Instant leaderStarted = leaderReached(k);
			if (leaderStarted != null) {
				when.add(new When(leaderStarted, SnapshotReason.LAP));
			}
		}
		for (TrackStatusPeriod p : race.trackStatus()) {
			SnapshotReason reason = switch (p.status()) {
				case SAFETY_CAR -> SnapshotReason.SAFETY_CAR;
				case VSC -> SnapshotReason.VSC;
				case RED -> SnapshotReason.RED_FLAG;
				default -> null;
			};
			if (reason != null) {
				when.add(new When(p.from(), reason));
			}
		}
		race.pits().values().forEach(stops -> stops.forEach(s -> when.add(new When(s.from(), SnapshotReason.PIT_STOP))));
		// one at every moment something changes, for the browser to look up (inputs are in time order)
		Instant last = null;
		for (Input input : inputs) {
			if (!input.at().equals(last)) {
				when.add(new When(input.at(), SnapshotReason.CHANGE));
				last = input.at();
			}
		}
		when.sort(Comparator.comparing(When::at).thenComparing(When::reason));

		List<Snapshot> taken = new ArrayList<>();
		taken.add(new Snapshot(Instant.MIN, SnapshotReason.START, 1, initial));
		DerivedState state = initial;
		Instant upTo = Instant.MIN;
		for (When w : when) {
			state = fold(state, upTo, w.at());
			upTo = w.at();
			int lap = w.reason() == SnapshotReason.LAP ? race.lapOf(leaderLapsAt(w.at())) : race.lapAt(w.at());
			taken.add(new Snapshot(w.at(), w.reason(), lap, state));
		}
		return List.copyOf(taken);
	}

	// when the leader first had `laps` laps done
	private Instant leaderReached(double laps) {
		return race.timelines().values().stream()
				.map(t -> t.reachedAt(laps))
				.filter(Objects::nonNull)
				.min(Instant::compareTo)
				.orElse(null);
	}

	private double leaderLapsAt(Instant at) {
		return race.timelines().values().stream().mapToDouble(t -> t.lapsDoneAt(at)).max().orElse(0);
	}

}
