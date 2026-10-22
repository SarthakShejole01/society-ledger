package com.societyledger.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.societyledger.support.DatabaseTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

/** With email switched on, the real sender is part of the application and the development one is not. */
@DatabaseTest
@TestPropertySource(properties = {
		"society-ledger.mail.enabled=true",
		"society-ledger.mail.from=Society Ledger <login@example.org>",
		"spring.mail.host=localhost",
		"spring.profiles.active=dev" })
class MailWiringTest {

	@Autowired
	private ApplicationContext context;
	@Autowired
	private HealthEndpoint health;

	@Test
	void the_real_email_sender_is_wired_when_email_is_enabled() {
		Map<String, OtpSender> senders = context.getBeansOfType(OtpSender.class);

		assertThat(senders.values()).anyMatch(s -> s instanceof SmtpOtpSender);
	}

	@Test
	void the_development_sender_is_not_wired_when_email_is_enabled_even_in_the_dev_profile() {
		Map<String, OtpSender> senders = context.getBeansOfType(OtpSender.class);

		assertThat(senders.values()).noneMatch(s -> s.getClass().getSimpleName().equals("DevOtpSender"));
	}

	@Test
	void an_unreachable_mail_server_does_not_make_the_application_unhealthy() {
		// spring.mail.host points at localhost, where nothing is listening.
		assertThat(health.health().getStatus().getCode()).isEqualTo("UP");
	}
}
