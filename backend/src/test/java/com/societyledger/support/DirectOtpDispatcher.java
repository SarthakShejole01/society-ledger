package com.societyledger.support;

import com.societyledger.auth.OtpDispatcher;

/** Runs delivery immediately, so tests see the email as soon as the call returns. */
public class DirectOtpDispatcher implements OtpDispatcher {

	@Override
	public void dispatch(Runnable task) {
		task.run();
	}
}
