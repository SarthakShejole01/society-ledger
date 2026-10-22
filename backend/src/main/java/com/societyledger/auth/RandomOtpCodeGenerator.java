package com.societyledger.auth;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
class RandomOtpCodeGenerator implements OtpCodeGenerator {

	private final SecureRandom random = new SecureRandom();

	@Override
	public String next() {
		return "%06d".formatted(random.nextInt(1_000_000));
	}
}
