package com.telemetrix.telemetry;

import com.telemetrix.telemetry.LapTrace.Sample;
import java.time.Duration;
import java.util.List;

// Fuel and ERS aren't in any data source: these are simple models, always shown as estimates.
// - Fuel: the 110 kg race maximum at the start, burned evenly over the race distance.
// - ERS: the MGU-K deploys its maximum 120 kW on full throttle (≥ 98 %), at most 4 MJ a lap, and
//   harvests 120 kW under braking, at most 2 MJ a lap (the regulations' limits; the MGU-H is ignored).
public final class Estimates {

	public static final double MAX_FUEL_KG = 110;

	static final double MGU_K_MJ_PER_S = 0.120;

	static final double MAX_DEPLOY_MJ = 4;

	static final double MAX_HARVEST_MJ = 2;

	static final int FULL_THROTTLE = 98;

	public static final String NOTE = "Estimates from simple models (fuel burned evenly from 110 kg; ERS from throttle and "
			+ "brake at the MGU-K's 120 kW limits), not measured data";

	public record Ers(double deployedMj, double harvestedMj) {
	}

	private Estimates() {
	}

	/** Fuel on board at the start of a lap. */
	public static double fuelKg(int lap, int totalLaps) {
		if (totalLaps <= 0) {
			return 0;
		}
		return Math.max(0, MAX_FUEL_KG * (1 - (double) (lap - 1) / totalLaps));
	}

	/** Energy deployed and harvested over a lap's samples (in time order). */
	public static Ers ers(List<Sample> samples) {
		double deployed = 0;
		double harvested = 0;
		for (int i = 1; i < samples.size(); i++) {
			Sample previous = samples.get(i - 1);
			double seconds = Duration.between(previous.at(), samples.get(i).at()).toMillis() / 1000.0;
			if (previous.throttle() != null && previous.throttle() >= FULL_THROTTLE) {
				deployed = Math.min(MAX_DEPLOY_MJ, deployed + MGU_K_MJ_PER_S * seconds);
			}
			if (previous.brake() != null && previous.brake() > 0) {
				harvested = Math.min(MAX_HARVEST_MJ, harvested + MGU_K_MJ_PER_S * seconds);
			}
		}
		return new Ers(deployed, harvested);
	}

}
