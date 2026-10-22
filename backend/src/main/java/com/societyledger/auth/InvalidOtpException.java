package com.societyledger.auth;

/** The code, number or society was wrong, expired or already used. Deliberately says nothing more. */
public class InvalidOtpException extends RuntimeException {

	public InvalidOtpException() {
		super("The code is not valid");
	}
}
