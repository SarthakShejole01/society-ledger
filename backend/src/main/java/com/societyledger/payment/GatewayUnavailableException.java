package com.societyledger.payment;

/** The provider could not be reached or refused the request. Safe to try again later. */
public class GatewayUnavailableException extends RuntimeException {

	public GatewayUnavailableException(Throwable cause) {
		super("The payment provider is not available", cause);
	}
}
