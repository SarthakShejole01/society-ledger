package com.societyledger.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Local development only: writes the code to the log instead of emailing it.
 * Active only under the "dev" profile. Never enable it where real members log in.
 */
@Component
@Profile("dev")
class DevOtpSender implements OtpSender {

	private static final Logger log = LoggerFactory.getLogger(DevOtpSender.class);

	@Override
	public void send(String email, String code) {
		log.warn("DEV ONLY: one-time code for {} is {}", email, code);
	}
}
