package com.telemetrix.eventstore;

public record StreamId(StreamType type, long id) {

	@Override
	public String toString() {
		return type + ":" + id;
	}

}
