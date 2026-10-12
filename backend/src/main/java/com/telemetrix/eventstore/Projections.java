package com.telemetrix.eventstore;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

// Every read model (docs/adr/0006). Rebuilding them all is safe at any time: each is worked out again from
// the event streams alone.
@Service
public class Projections {

	private final List<Projection> projections;

	Projections(List<Projection> projections) {
		this.projections = projections;
	}

	/** Throws every read model away to be rebuilt from the events; returns their names. */
	public List<String> rebuildAll() {
		List<String> rebuilt = new ArrayList<>();
		for (Projection projection : projections) {
			projection.rebuild();
			rebuilt.add(projection.name());
		}
		return rebuilt;
	}

}
