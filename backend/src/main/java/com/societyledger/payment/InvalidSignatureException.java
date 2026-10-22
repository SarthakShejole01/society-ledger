package com.societyledger.payment;

/** The proof of payment does not match the order we created. */
public class InvalidSignatureException extends RuntimeException {

	public InvalidSignatureException() {
		super("The payment could not be verified");
	}
}
