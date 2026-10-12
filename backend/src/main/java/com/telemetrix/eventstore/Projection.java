package com.telemetrix.eventstore;

// A read model worked out from the event streams (docs/adr/0006). rebuild() throws away what it holds; it
// is built again from the events alone, never from the imported data.
public interface Projection {

	String name();

	void rebuild();

}
