package com.societyledger.payment;

public class InvalidWebhookException extends RuntimeException {

	public InvalidWebhookException() {
		super("The notification could not be trusted");
	}
}
