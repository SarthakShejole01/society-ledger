package com.societyledger.payment;

public class FlatNotFoundException extends RuntimeException {

	public FlatNotFoundException() {
		super("Flat not found");
	}
}
