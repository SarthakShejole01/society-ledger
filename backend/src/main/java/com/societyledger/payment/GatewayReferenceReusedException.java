package com.societyledger.payment;

public class GatewayReferenceReusedException extends RuntimeException {

	public GatewayReferenceReusedException() {
		super("This gateway reference belongs to another payment");
	}
}
