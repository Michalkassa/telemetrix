package com.telemetrix.eventstore;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectionsTest {

	@Test
	void rebuildsEachReadModelInTurn() {
		List<String> rebuilt = new ArrayList<>();
		var projections = new Projections(List.of(projection("timeline", rebuilt), projection("standings", rebuilt)));

		assertThat(projections.rebuildAll()).containsExactly("timeline", "standings");
		assertThat(rebuilt).containsExactly("timeline", "standings");
	}

	private static Projection projection(String name, List<String> rebuilt) {
		return new Projection() {

			@Override
			public String name() {
				return name;
			}

			@Override
			public void rebuild() {
				rebuilt.add(name);
			}

		};
	}

}
