package com.telemetrix.ingestion;

import com.telemetrix.ingestion.openf1.OpenF1Lap;
import com.telemetrix.ingestion.openf1.OpenF1PitStop;
import com.telemetrix.ingestion.openf1.OpenF1Stint;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

// Checks a session's laps, stints and pit stops for consistency. Problems are reported, not fixed:
// the data is still stored, so a gap shows up in the replay rather than vanishing silently.
@Component
class LapConsistencyValidator {

	enum Severity {
		ERROR, WARNING
	}

	record Finding(Severity severity, String message) {
	}

	// OpenF1 rounds to milliseconds per sector; real sums differ by a few ms at most
	private static final double SECTOR_TOLERANCE_SECONDS = 0.05;

	List<Finding> check(List<OpenF1Lap> laps, List<OpenF1Stint> stints, List<OpenF1PitStop> pits) {
		List<Finding> findings = new ArrayList<>();
		Map<Integer, List<OpenF1Lap>> byCar = laps.stream()
				.collect(Collectors.groupingBy(OpenF1Lap::driverNumber, TreeMap::new, Collectors.toList()));
		byCar.forEach((car, carLaps) -> {
			checkLapNumbers(car, carLaps, findings);
			carLaps.forEach(lap -> checkSectors(lap, findings));
		});
		checkPitStopsChangeTyres(stints, pits, findings);
		return findings;
	}

	// Lap numbers run 1, 2, 3, ... without gaps or repeats.
	private static void checkLapNumbers(int car, List<OpenF1Lap> carLaps, List<Finding> findings) {
		var numbers = carLaps.stream().map(OpenF1Lap::lapNumber).sorted().toList();
		for (int i = 1; i < numbers.size(); i++) {
			int previous = numbers.get(i - 1);
			int current = numbers.get(i);
			if (current != previous + 1) {
				findings.add(new Finding(Severity.ERROR, "car " + car + ": lap " + previous + " is followed by lap "
						+ current + (current == previous ? " (twice)" : "")));
			}
		}
	}

	// When all three sectors are there, they add up to the lap time. A missing sector is allowed: the
	// lap is stored as partial.
	private static void checkSectors(OpenF1Lap lap, List<Finding> findings) {
		if (lap.durationSector1() == null || lap.durationSector2() == null || lap.durationSector3() == null
				|| lap.lapDuration() == null) {
			return;
		}
		double sum = lap.durationSector1() + lap.durationSector2() + lap.durationSector3();
		if (Math.abs(sum - lap.lapDuration()) > SECTOR_TOLERANCE_SECONDS) {
			findings.add(new Finding(Severity.ERROR, "car %d lap %d: sectors add up to %.3f s but the lap took %.3f s"
					.formatted(lap.driverNumber(), lap.lapNumber(), sum, lap.lapDuration())));
		}
	}

	// A car that pits on lap N should start a new stint on lap N or N + 1.
	private static void checkPitStopsChangeTyres(List<OpenF1Stint> stints, List<OpenF1PitStop> pits,
			List<Finding> findings) {
		Map<Integer, Set<Integer>> stintStarts = stints.stream()
				.filter(s -> s.lapStart() != null)
				.collect(Collectors.groupingBy(OpenF1Stint::driverNumber,
						Collectors.mapping(OpenF1Stint::lapStart, Collectors.toSet())));
		for (OpenF1PitStop pit : pits) {
			if (pit.lapNumber() == null) {
				continue;
			}
			Set<Integer> starts = stintStarts.getOrDefault(pit.driverNumber(), Set.of());
			if (!starts.contains(pit.lapNumber()) && !starts.contains(pit.lapNumber() + 1)) {
				findings.add(new Finding(Severity.WARNING, "car " + pit.driverNumber() + " pitted on lap "
						+ pit.lapNumber() + " but no tyre stint starts on lap " + (pit.lapNumber() + 1)));
			}
		}
	}

}
