package com.societyledger.support;

import com.societyledger.auth.OtpSender;
import java.util.ArrayList;
import java.util.List;

/** Captures the codes the app would email, so tests can read them. */
public class RecordingOtpSender implements OtpSender {

	public record Sent(String email, String code) {
	}

	private final List<Sent> sent = new ArrayList<>();

	@Override
	public synchronized void send(String email, String code) {
		sent.add(new Sent(email, code));
	}

	public synchronized List<Sent> sent() {
		return List.copyOf(sent);
	}

	public synchronized void clear() {
		sent.clear();
	}
}
