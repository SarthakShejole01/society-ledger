package com.societyledger.auth;

/** Runs the delivery of a code away from the request, so how long it takes never shows to the caller. */
public interface OtpDispatcher {

	void dispatch(Runnable task);
}
