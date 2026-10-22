package com.societyledger.auth;

public class TooManyOtpRequestsException extends RuntimeException {

	public TooManyOtpRequestsException() {
		super("Too many code requests, try again later");
	}
}
