package com.societyledger.support;

import com.societyledger.auth.OtpSender;
import java.util.ArrayList;
import java.util.List;

/** Captures the codes the app would email, so tests can read them. */
public class RecordingOtpSender implements OtpSender {

	public record Sent(String email, String code) {
	}

	private final List<Sent> sent = new ArrayList<>();
	private RuntimeException failure;
	private Runnable beforeSend;

	@Override
	public synchronized void send(String email, String code) {
		if (beforeSend != null) {
			beforeSend.run();
		}
		if (failure != null) {
			throw failure;
		}
		sent.add(new Sent(email, code));
	}

	public synchronized List<Sent> sent() {
		return List.copyOf(sent);
	}

	/** Makes every send fail, like a mail server that is down. */
	public synchronized void failWith(RuntimeException failure) {
		this.failure = failure;
	}

	/** Runs something at the moment of sending, to look at what exists by then. */
	public synchronized void beforeSend(Runnable action) {
		this.beforeSend = action;
	}

	public synchronized void clear() {
		sent.clear();
		failure = null;
		beforeSend = null;
	}
}
