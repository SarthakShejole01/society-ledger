package com.societyledger.common.web;

public class ForbiddenException extends RuntimeException {

	public ForbiddenException() {
		super("You are not allowed to do this");
	}
}
