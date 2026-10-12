package com.telemetrix.state;

import com.telemetrix.event.RaceData;
import com.telemetrix.replay.ProgressTimeline;
import com.telemetrix.state.SessionEvent.Detected;
import com.telemetrix.state.SessionEvent.DrsChanged;
import com.telemetrix.state.SessionEvent.FlagChanged;
import com.telemetrix.state.SessionEvent.PenaltyGiven;
import com.telemetrix.state.SessionEvent.PitIn;
import com.telemetrix.state.SessionEvent.PitOut;
import com.telemetrix.state.SessionEvent.SectorFlagged;
import com.telemetrix.state.SessionEvent.SessionRecorded;
import com.telemetrix.state.SessionEvent.TrackCleared;
import com.telemetrix.state.SessionEvent.TyresFitted;
import com.telemetrix.state.SessionEvent.WeatherReported;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// A race's state at any moment (RaceState). Positions, laps and gaps come straight from the timing
// (instant, docs/adr/0004); the rest — flag, tyres, pit stops, penalties, weather — is a fold over the
// session's event stream (SessionEvent, docs/adr/0006) in order. Snapshots of that folded part, taken every
// 5 s, every lap and at key events, are a cache: a seek only replays the few events since the nearest one,
// so scrubbing stays instant anywhere. stateAtEvent is time travel: the state right after event #n.
public final class RaceStateEngine {

	static final Duration SNAPSHOT_INTERVAL = Duration.ofSeconds(5);

	// within this many seconds of the car ahead (at the detection point; here: anywhere) a car may use DRS
	static final double DRS_GAP = 1.0;

	/** A state-changing event at a moment, with its number in the stream. */
	private record Numbered(int seq, SessionEvent event) {

		Instant at() {
			return event.at();
		}

	}

	private final RaceData race;

	private final List<SessionEvent> events;

	private final DerivedState initial;

	// the state-changing events at a moment, in stream order
	private final List<Numbered> inputs;

	private final List<Snapshot> snapshots;

	private RaceStateEngine(RaceData race, List<SessionEvent> events) {
		this.race = race;
		this.events = List.copyOf(events);
		List<Numbered> before = new ArrayList<>();
		List<Numbered> timed = new ArrayList<>();
		for (int i = 0; i < this.events.size(); i++) {
			SessionEvent event = this.events.get(i);
			if (changesState(event)) {
				(event.at() == null ? before : timed).add(new Numbered(i + 1, event));
			}
		}
		this.inputs = List.copyOf(timed);
		this.initial = foldAll(new DerivedState(Flag.GREEN, Map.of(), false, null, Map.of()), before);
		this.snapshots = takeSnapshots();
	}

	/** The engine for a race's imported data (its stream worked out here, without timeline events). */
	public static RaceStateEngine of(RaceData race) {
		return of(race, SessionEvents.derive(race, List.of()));
	}

	/** The engine for a session's stream; `timing` gives the cars' positions (its other data is unused). */
	public static RaceStateEngine of(RaceData timing, List<SessionEvent> events) {
		return new RaceStateEngine(timing, events);
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

	/** The session's stream; event #n is `events().get(n - 1)`. */
	public List<SessionEvent> events() {
		return events;
	}

	/**
	 * Time travel: the state right after event #`seq` (from 1) and nothing later, even at the same moment;
	 * at the event's time (the clock's zero for one before the start).
	 */
	public RaceState stateAtEvent(int seq) {
		if (seq < 1 || seq > events.size()) {
			throw new IllegalArgumentException("No event #" + seq + " in a stream of " + events.size());
		}
		Instant at = events.get(seq - 1).at();
		List<Numbered> upTo = inputs.stream().filter(n -> n.seq() <= seq).toList();
		return state(at == null ? race.clockStart() : at, foldAll(initial, upTo));
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
			folding.apply(inputs.get(i).event());
		}
		return folding.freeze();
	}

	private static DerivedState foldAll(DerivedState state, List<Numbered> events) {
		var folding = new Folding(state);
		events.forEach(n -> folding.apply(n.event()));
		return folding.freeze();
	}

	private static boolean changesState(SessionEvent event) {
		return !(event instanceof SessionRecorded || event instanceof Detected);
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

		WeatherNow weather;

		final Map<Integer, DerivedState.Car> cars;

		Folding(DerivedState state) {
			this.flag = state.flag();
			this.yellowSectors = new HashMap<>(state.yellowSectors());
			this.drsEnabled = state.drsEnabled();
			this.weather = state.weather();
			this.cars = new HashMap<>(state.cars());
		}

		void apply(SessionEvent event) {
			switch (event) {
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
				case WeatherReported w -> weather = new WeatherNow(w.airTemperature(), w.trackTemperature(),
						w.humidity(), w.rainfall(), w.windSpeed());
				case SessionRecorded r -> {
				}
				case Detected d -> {
				}
			}
		}

		private void update(int car, java.util.function.UnaryOperator<DerivedState.Car> change) {
			cars.put(car, change.apply(cars.getOrDefault(car, DerivedState.Car.NONE)));
		}

		DerivedState freeze() {
			return new DerivedState(flag, yellowSectors, drsEnabled, weather, cars);
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
		return new RaceState(t, lap, derived.flag(), derived.yellowSectors(), derived.drsEnabled(), derived.weather(), cars);
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

	// seconds since `other` was where this car is now
	private static Double behind(ProgressTimeline other, double lapsDone, Instant at) {
		Instant otherWasHere = other.reachedAt(lapsDone);
		return otherWasHere == null ? null : Math.max(0, Duration.between(otherWasHere, at).toMillis()) / 1000.0;
	}

	private static double round(double value, int factor) {
		return Math.round(value * factor) / (double) factor;
	}

	// ---- building the inputs and snapshots

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
		for (Numbered n : inputs) {
			SnapshotReason reason = switch (n.event()) {
				case FlagChanged f when f.flag() == Flag.SAFETY_CAR -> SnapshotReason.SAFETY_CAR;
				case FlagChanged f when f.flag() == Flag.VSC -> SnapshotReason.VSC;
				case FlagChanged f when f.flag() == Flag.RED -> SnapshotReason.RED_FLAG;
				case PitIn p -> SnapshotReason.PIT_STOP;
				default -> null;
			};
			if (reason != null) {
				when.add(new When(n.at(), reason));
			}
		}
		// one at every moment something the browser shows changes, for it to look up (inputs are in time
		// order; the weather it gets with the replay)
		Instant last = null;
		for (Numbered n : inputs) {
			if (!(n.event() instanceof WeatherReported) && !n.at().equals(last)) {
				when.add(new When(n.at(), SnapshotReason.CHANGE));
				last = n.at();
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
