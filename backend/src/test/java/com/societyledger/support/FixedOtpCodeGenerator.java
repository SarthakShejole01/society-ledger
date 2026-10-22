package com.societyledger.support;

import com.societyledger.auth.OtpCodeGenerator;
import java.util.ArrayDeque;
import java.util.Deque;

/** Hands out the codes a test queued, then "111111". */
public class FixedOtpCodeGenerator implements OtpCodeGenerator {

	private final Deque<String> queued = new ArrayDeque<>();

	public synchronized void queue(String... codes) {
		queued.clear();
		for (String code : codes) {
			queued.add(code);
		}
	}

	@Override
	public synchronized String next() {
		return queued.isEmpty() ? "111111" : queued.poll();
	}
}
