package com.societyledger.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock tests can move, so time-dependent rules never depend on the real clock. */
public class MutableClock extends Clock {

	private volatile Instant now = Instant.parse("2026-10-01T00:00:00Z");

	public void set(String instant) {
		this.now = Instant.parse(instant);
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return this;
	}

	@Override
	public Instant instant() {
		return now;
	}
}
