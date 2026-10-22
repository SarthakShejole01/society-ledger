package com.societyledger.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.Address;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Sends through a real SMTP conversation with an in-process mail server. */
class SmtpOtpSenderTest {

	@RegisterExtension
	static final GreenMailExtension MAIL = new GreenMailExtension(ServerSetupTest.SMTP);

	private SmtpOtpSender sender;

	@BeforeEach
	void aSenderPointedAtTheTestMailServer() {
		JavaMailSenderImpl mail = new JavaMailSenderImpl();
		mail.setHost("localhost");
		mail.setPort(ServerSetupTest.SMTP.getPort());
		sender = new SmtpOtpSender(mail, "Society Ledger <login@example.org>");
	}

	private MimeMessage delivered() throws Exception {
		assertThat(MAIL.getReceivedMessages()).hasSize(1);
		return MAIL.getReceivedMessages()[0];
	}

	@Test
	void sends_one_email_to_the_members_address() throws Exception {
		sender.send("rajesh@example.com", "482917");

		Address[] to = delivered().getAllRecipients();
		assertThat(to).hasSize(1);
		assertThat(to[0].toString()).isEqualTo("rajesh@example.com");
	}

	@Test
	void comes_from_the_configured_address() throws Exception {
		sender.send("rajesh@example.com", "482917");

		assertThat(delivered().getFrom()[0].toString()).contains("login@example.org");
	}

	@Test
	void the_body_carries_the_code_and_how_long_it_lasts() throws Exception {
		sender.send("rajesh@example.com", "482917");

		String body = (String) delivered().getContent();
		assertThat(body).contains("482917").contains("10 minutes").contains("only once");
	}

	@Test
	void the_subject_does_not_show_the_code() throws Exception {
		sender.send("rajesh@example.com", "482917");

		assertThat(delivered().getSubject()).isEqualTo("Your Society Ledger login code").doesNotContain("482917");
	}

	@Test
	void warns_the_reader_not_to_share_the_code() throws Exception {
		sender.send("rajesh@example.com", "482917");

		assertThat((String) delivered().getContent()).contains("Never share this code");
	}

	@Test
	void a_mail_server_that_is_down_raises_an_error_for_the_caller_to_handle() {
		JavaMailSenderImpl unreachable = new JavaMailSenderImpl();
		unreachable.setHost("localhost");
		unreachable.setPort(1);
		SmtpOtpSender broken = new SmtpOtpSender(unreachable, "login@example.org");

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> broken.send("rajesh@example.com", "482917"))
				.isInstanceOf(RuntimeException.class);
	}
}
