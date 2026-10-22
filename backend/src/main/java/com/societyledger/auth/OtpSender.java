package com.societyledger.auth;

/** Delivers a one-time code. Email today; SMS or WhatsApp can be added behind the same interface. */
public interface OtpSender {

	void send(String email, String code);
}
