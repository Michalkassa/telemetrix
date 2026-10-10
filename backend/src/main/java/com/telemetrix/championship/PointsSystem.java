package com.telemetrix.championship;

// The points a finishing position earns — one strategy per era (the Strategy pattern). Every value here
// comes from the data: the points the history awarded, season by season (PointsSystemTest checks each
// rule against every recorded result). Historical standings add up the points actually awarded (they
// include shared drives); these rules are for points that haven't been awarded yet — "if the race
// finished now" during a replay.
public interface PointsSystem {

	/** Full points; HALF for a race stopped early (pre-2022 rules), DOUBLE for Abu Dhabi 2014. */
	enum Scale {
		FULL(1), HALF(0.5), DOUBLE(2);

		final double factor;

		Scale(double factor) {
			this.factor = factor;
		}
	}

	/** Points for a Grand Prix finishing position (null: not classified), and the fastest lap. */
	double race(Integer position, boolean fastestLap, Scale scale);

	/** Points for a sprint finishing position. */
	double sprint(Integer position);

	static PointsSystem forSeason(int season) {
		if (season <= 1959) {
			return Era.FIFTIES;
		}
		if (season == 1960) {
			return Era.SIXTY;
		}
		if (season <= 1990) {
			return Era.NINE_SIX;
		}
		if (season <= 2002) {
			return Era.TEN_SIX;
		}
		if (season <= 2009) {
			return Era.TEN_EIGHT;
		}
		if (season <= 2018) {
			return Era.TWENTY_FIVE;
		}
		if (season == 2019 || season == 2020) {
			return Era.TWENTY_FIVE_FASTEST_LAP;
		}
		if (season == 2021) {
			return Era.TWENTY_FIVE_FASTEST_LAP_SPRINT_3;
		}
		if (season <= 2024) {
			return Era.TWENTY_FIVE_FASTEST_LAP_SPRINT_8;
		}
		return Era.TWENTY_FIVE_SPRINT_8;
	}

	/** Who earns the fastest-lap point. */
	enum FastestLap {
		NONE, ANYONE, TOP_TEN
	}

	/** A points table: the strategy for one era. */
	record Era(double[] racePoints, FastestLap fastestLap, double[] sprintPoints) implements PointsSystem {

		static final double[] NONE = {};

		static final double[] TWENTY_FIVE_TABLE = { 25, 18, 15, 12, 10, 8, 6, 4, 2, 1 };

		static final double[] SPRINT_8 = { 8, 7, 6, 5, 4, 3, 2, 1 };

		static final Era FIFTIES = new Era(new double[] { 8, 6, 4, 3, 2 }, FastestLap.ANYONE, NONE);

		static final Era SIXTY = new Era(new double[] { 8, 6, 4, 3, 2, 1 }, FastestLap.NONE, NONE);

		static final Era NINE_SIX = new Era(new double[] { 9, 6, 4, 3, 2, 1 }, FastestLap.NONE, NONE);

		static final Era TEN_SIX = new Era(new double[] { 10, 6, 4, 3, 2, 1 }, FastestLap.NONE, NONE);

		static final Era TEN_EIGHT = new Era(new double[] { 10, 8, 6, 5, 4, 3, 2, 1 }, FastestLap.NONE, NONE);

		static final Era TWENTY_FIVE = new Era(TWENTY_FIVE_TABLE, FastestLap.NONE, NONE);

		static final Era TWENTY_FIVE_FASTEST_LAP = new Era(TWENTY_FIVE_TABLE, FastestLap.TOP_TEN, NONE);

		static final Era TWENTY_FIVE_FASTEST_LAP_SPRINT_3 = new Era(TWENTY_FIVE_TABLE, FastestLap.TOP_TEN,
				new double[] { 3, 2, 1 });

		static final Era TWENTY_FIVE_FASTEST_LAP_SPRINT_8 = new Era(TWENTY_FIVE_TABLE, FastestLap.TOP_TEN, SPRINT_8);

		static final Era TWENTY_FIVE_SPRINT_8 = new Era(TWENTY_FIVE_TABLE, FastestLap.NONE, SPRINT_8);

		@Override
		public double race(Integer position, boolean fastestLap, Scale scale) {
			double points = table(racePoints, position) * scale.factor;
			// the fastest-lap point needs a full-points race (none at Spa 2021, run behind the safety car)
			boolean earnsFastestLap = fastestLap && scale != Scale.HALF && switch (this.fastestLap) {
				case NONE -> false;
				case ANYONE -> true;
				case TOP_TEN -> position != null && position <= 10;
			};
			return points + (earnsFastestLap ? 1 : 0);
		}

		@Override
		public double sprint(Integer position) {
			return table(sprintPoints, position);
		}

		private static double table(double[] points, Integer position) {
			return position != null && position >= 1 && position <= points.length ? points[position - 1] : 0;
		}

	}

}
