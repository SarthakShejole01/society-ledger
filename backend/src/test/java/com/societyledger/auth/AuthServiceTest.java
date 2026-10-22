package com.societyledger.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.societyledger.common.Money;
import com.societyledger.common.tenant.SocietyScope;
import com.societyledger.society.Flat;
import com.societyledger.society.FlatRepository;
import com.societyledger.support.DatabaseTest;
import com.societyledger.support.FixedOtpCodeGenerator;
import com.societyledger.support.MutableClock;
import com.societyledger.support.PostgresTestSupport;
import com.societyledger.support.RecordingOtpSender;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@DatabaseTest
class AuthServiceTest {

	private static final UUID SUNRISE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private static final UUID LAKEVIEW = UUID.fromString("00000000-0000-0000-0000-00000000000b");
	private static final String MOBILE = "9876543210";
	private static final String EMAIL = "rajesh@example.com";

	@Autowired
	private AuthService auth;
	@Autowired
	private SocietyScope scope;
	@Autowired
	private FlatRepository flats;
	@Autowired
	private RecordingOtpSender sender;
	@Autowired
	private FixedOtpCodeGenerator codes;
	@Autowired
	private MutableClock clock;

	private final JdbcTemplate admin = PostgresTestSupport.admin();
	private Flat flat304;

	@BeforeEach
	void rajeshOwnsFlat304AtSunrise() {
		admin.execute("truncate sessions, otp_challenges, members, payments, charges, flats, societies cascade");
		admin.update("insert into societies (id, code, name) values (?, 'sunrise', 'Sunrise Apartments')", SUNRISE);
		admin.update("insert into societies (id, code, name) values (?, 'lakeview', 'Lakeview Heights')", LAKEVIEW);
		flat304 = scope.inSociety(SUNRISE, () -> flats.add(SUNRISE, "304", 3, Money.rupees(1500)));
		admin.update("""
				insert into members (id, society_id, flat_id, name, mobile, email, role)
				values (gen_random_uuid(), ?, ?, 'Rajesh K', ?, ?, 'OWNER')""", SUNRISE, flat304.id(), MOBILE, EMAIL);
		sender.clear();
		codes.queue("482917");
		clock.set("2026-10-05T04:30:00Z");
	}

	@Test
	void a_registered_member_receives_a_code_by_email() {
		auth.requestOtp("sunrise", MOBILE);

		assertThat(sender.sent()).containsExactly(new RecordingOtpSender.Sent(EMAIL, "482917"));
	}

	@Test
	void a_failing_mail_server_does_not_change_the_answer() {
		sender.failWith(new IllegalStateException("smtp is down"));

		auth.requestOtp("sunrise", MOBILE);

		assertThat(admin.queryForObject("select count(*) from otp_challenges", Integer.class)).isEqualTo(1);
	}

	@Test
	void the_code_is_sent_only_after_it_has_been_saved() {
		java.util.concurrent.atomic.AtomicInteger savedWhenSent = new java.util.concurrent.atomic.AtomicInteger(-1);
		sender.beforeSend(() -> savedWhenSent.set(admin.queryForObject("select count(*) from otp_challenges", Integer.class)));

		auth.requestOtp("sunrise", MOBILE);

		assertThat(savedWhenSent.get()).isEqualTo(1);
	}

	@Test
	void the_code_is_not_stored_in_plain_text() {
		auth.requestOtp("sunrise", MOBILE);

		String stored = admin.queryForObject("select code_hash from otp_challenges", String.class);
		assertThat(stored).doesNotContain("482917");
	}

	@Test
	void an_unknown_mobile_number_gets_no_email_and_no_error() {
		auth.requestOtp("sunrise", "9000000000");

		assertThat(sender.sent()).isEmpty();
	}

	@Test
	void the_right_code_logs_the_member_in() {
		auth.requestOtp("sunrise", MOBILE);

		AuthSession session = auth.verifyOtp("sunrise", MOBILE, "482917");

		assertThat(session.principal().role()).isEqualTo(Role.OWNER);
		assertThat(session.principal().societyId()).isEqualTo(SUNRISE);
		assertThat(session.principal().flatId()).isEqualTo(flat304.id());
		assertThat(session.token()).isNotBlank();
	}

	@Test
	void a_wrong_code_is_rejected() {
		auth.requestOtp("sunrise", MOBILE);

		assertThatThrownBy(() -> auth.verifyOtp("sunrise", MOBILE, "000000"))
				.isInstanceOf(InvalidOtpException.class);
	}

	@Test
	void a_code_cannot_be_used_twice() {
		auth.requestOtp("sunrise", MOBILE);
		auth.verifyOtp("sunrise", MOBILE, "482917");

		assertThatThrownBy(() -> auth.verifyOtp("sunrise", MOBILE, "482917"))
				.isInstanceOf(InvalidOtpException.class);
	}

	@Test
	void a_code_expires_after_ten_minutes() {
		auth.requestOtp("sunrise", MOBILE);
		clock.set("2026-10-05T04:40:01Z");

		assertThatThrownBy(() -> auth.verifyOtp("sunrise", MOBILE, "482917"))
				.isInstanceOf(InvalidOtpException.class);
	}

	@Test
	void five_wrong_codes_lock_the_challenge_even_for_the_right_code() {
		auth.requestOtp("sunrise", MOBILE);
		for (int i = 0; i < 5; i++) {
			assertThatThrownBy(() -> auth.verifyOtp("sunrise", MOBILE, "000000"))
					.isInstanceOf(InvalidOtpException.class);
		}

		assertThatThrownBy(() -> auth.verifyOtp("sunrise", MOBILE, "482917"))
				.isInstanceOf(InvalidOtpException.class);
	}

	@Test
	void a_new_request_replaces_the_previous_code() {
		codes.queue("111222", "333444");
		auth.requestOtp("sunrise", MOBILE);
		auth.requestOtp("sunrise", MOBILE);

		assertThatThrownBy(() -> auth.verifyOtp("sunrise", MOBILE, "111222"))
				.isInstanceOf(InvalidOtpException.class);
		assertThat(auth.verifyOtp("sunrise", MOBILE, "333444").token()).isNotBlank();
	}

	@Test
	void a_fourth_request_within_ten_minutes_is_refused() {
		auth.requestOtp("sunrise", MOBILE);
		auth.requestOtp("sunrise", MOBILE);
		auth.requestOtp("sunrise", MOBILE);

		assertThatThrownBy(() -> auth.requestOtp("sunrise", MOBILE))
				.isInstanceOf(TooManyOtpRequestsException.class);
		assertThat(sender.sent()).hasSize(3);
	}

	@Test
	void a_member_cannot_log_in_through_another_society() {
		auth.requestOtp("sunrise", MOBILE);

		assertThatThrownBy(() -> auth.verifyOtp("lakeview", MOBILE, "482917"))
				.isInstanceOf(InvalidOtpException.class);
	}

	@Test
	void a_session_token_identifies_the_member() {
		auth.requestOtp("sunrise", MOBILE);
		AuthSession session = auth.verifyOtp("sunrise", MOBILE, "482917");

		assertThat(auth.authenticate(session.token())).contains(session.principal());
	}

	@Test
	void the_session_token_is_not_stored_in_plain_text() {
		auth.requestOtp("sunrise", MOBILE);
		AuthSession session = auth.verifyOtp("sunrise", MOBILE, "482917");

		String stored = admin.queryForObject("select token_hash from sessions", String.class);
		assertThat(stored).isNotEqualTo(session.token()).doesNotContain(session.token());
	}

	@Test
	void a_session_expires_after_twelve_hours() {
		auth.requestOtp("sunrise", MOBILE);
		AuthSession session = auth.verifyOtp("sunrise", MOBILE, "482917");
		clock.set("2026-10-05T16:30:01Z");

		assertThat(auth.authenticate(session.token())).isEmpty();
	}

	@Test
	void a_made_up_token_identifies_nobody() {
		assertThat(auth.authenticate("not-a-token")).isEmpty();
		assertThat(auth.authenticate(SUNRISE + ".forged")).isEmpty();
	}
}
