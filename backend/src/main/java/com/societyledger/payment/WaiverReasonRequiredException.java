package com.societyledger.payment;

public class WaiverReasonRequiredException extends RuntimeException {

	public WaiverReasonRequiredException() {
		super("A reason is required to waive a late fee");
	}
}
