package com.telemetrix.circuit;

import com.telemetrix.common.NotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CircuitLayoutService {

	private final CircuitLayoutStore store;

	CircuitLayoutService(CircuitLayoutStore store) {
		this.store = store;
	}

	public CircuitLayout layout(String circuitRef, int year) {
		return store.findByRef(circuitRef, year)
				.orElseThrow(() -> new NotFoundException("Track map of " + circuitRef + " in", year));
	}

}
