package com.societyledger.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Emails the one-time code. Switched on with society-ledger.mail.enabled=true. */
@Component
@ConditionalOnProperty(prefix = "society-ledger.mail", name = "enabled", havingValue = "true")
class SmtpOtpSender implements OtpSender {

	private static final String SUBJECT = "Your Society Ledger login code";

	private final JavaMailSender mail;
	private final String from;

	SmtpOtpSender(JavaMailSender mail, @Value("${society-ledger.mail.from}") String from) {
		this.mail = mail;
		this.from = from;
	}

	@Override
	public void send(String email, String code) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(email);
		message.setSubject(SUBJECT);
		message.setText("""
				Your login code is %s.

				It works for 10 minutes and only once. If you did not ask for it, you can ignore this email.

				Never share this code with anyone. The society treasurer will not ask you for it.
				""".formatted(code));
		mail.send(message);
	}
}
