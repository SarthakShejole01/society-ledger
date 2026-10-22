package com.societyledger.payment;

public class AmountMismatchException extends RuntimeException {

	public AmountMismatchException() {
		super("The amount paid does not match the amount due");
	}
}
