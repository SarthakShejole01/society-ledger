package com.societyledger.payment;

public class AlreadyPaidException extends RuntimeException {

	public AlreadyPaidException() {
		super("This month is already paid");
	}
}
