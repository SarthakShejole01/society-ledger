package com.societyledger.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.societyledger.billing.StatusBoardService;
import com.societyledger.common.Money;
import com.societyledger.common.tenant.SocietyScope;
import com.societyledger.society.Flat;
import com.societyledger.society.FlatRepository;
import com.societyledger.support.DatabaseTest;
import com.societyledger.support.MutableClock;
import com.societyledger.support.PostgresTestSupport;
import com.societyledger.support.RecordingGateway;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@DatabaseTest
class GatewayPaymentTest {

	private static final UUID SUNRISE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private static final UUID LAKEVIEW = UUID.fromString("00000000-0000-0000-0000-00000000000b");
	private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

	@Autowired
	private PaymentService payments;
	@Autowired
	private StatusBoardService board;
	@Autowired
	private SocietyScope scope;
	@Autowired
	private FlatRepository flats;
	@Autowired
	private MutableClock clock;
	@Autowired
	private RecordingGateway gateway;

	private final JdbcTemplate admin = PostgresTestSupport.admin();
	private Flat flat304;
	private Flat lakeviewFlat;

	@BeforeEach
	void twoSocietiesAndAnEarlyOctoberMorning() {
		admin.execute("truncate audit_log, sessions, otp_challenges, members, payments, charges, flats, societies cascade");
		admin.update("insert into societies (id, code, name) values (?, 'sunrise', 'Sunrise Apartments')", SUNRISE);
		admin.update("insert into societies (id, code, name) values (?, 'lakeview', 'Lakeview Heights')", LAKEVIEW);
		flat304 = scope.inSociety(SUNRISE, () -> flats.add(SUNRISE, "304", 3, Money.rupees(1500)));
		lakeviewFlat = scope.inSociety(LAKEVIEW, () -> flats.add(LAKEVIEW, "A-1", 1, Money.rupees(2000)));
		gateway.reset();
		clock.set("2026-10-05T04:30:00Z");
	}

	private boolean paid(UUID society, String number) {
		return board.forMonth(society, OCTOBER).stream().filter(s -> s.number().equals(number)).findFirst().orElseThrow().paid();
	}

	// --- starting ---

	@Test
	void starting_a_payment_creates_an_order_for_the_total() {
		clock.set("2026-10-20T10:00:00Z");

		PaymentIntent intent = payments.start(SUNRISE, flat304.id(), OCTOBER, "Rajesh K");

		assertThat(gateway.orders()).hasSize(1);
		RecordingGateway.Order order = gateway.orders().get(0);
		assertThat(order.paymentId()).isEqualTo(intent.paymentId());
		assertThat(order.amount()).isEqualTo(Money.rupees(1600));
		assertThat(intent.gatewayOrderId()).isEqualTo(order.orderId());
		assertThat(intent.checkoutKey()).isEqualTo("key_test");
	}

	@Test
	void the_order_id_is_kept_with_the_payment() {
		PaymentIntent intent = payments.start(SUNRISE, flat304.id(), OCTOBER, "Rajesh K");

		assertThat(admin.queryForObject("select gateway_order_id from payments", String.class)).isEqualTo(intent.gatewayOrderId());
	}

	@Test
	void a_gateway_outage_leaves_no_pending_payment_behind() {
		gateway.goDown();

		assertThatThrownBy(() -> payments.start(SUNRISE, flat304.id(), OCTOBER, "Rajesh K"))
				.isInstanceOf(GatewayUnavailableException.class);

		assertThat(admin.queryForObject("select count(*) from payments", Integer.class)).isZero();
	}

	// --- the browser's proof ---

	@Test
	void a_valid_proof_from_the_browser_marks_the_flat_paid() {
		PaymentIntent intent = payments.start(SUNRISE, flat304.id(), OCTOBER, "Rajesh K");

		payments.confirmCheckout(SUNRISE, intent.paymentId(), intent.gatewayOrderId(), "pay_1", "good-signature");

		assertThat(paid(SUNRISE, "304")).isTrue();
		assertThat(admin.queryForObject("select gateway_reference from payments", String.class)).isEqualTo("pay_1");
	}

	@Test
	void a_forged_proof_is_rejected_and_the_flat_stays_pending() {
		PaymentIntent intent = payments.start(SUNRISE, flat304.id(), OCTOBER, "Rajesh K");

		assertThatThrownBy(() -> payments.confirmCheckout(SUNRISE, intent.paymentId(), intent.gatewayOrderId(), "pay_1", "forged"))
				.isInstanceOf(InvalidSignatureException.class);

		assertThat(paid(SUNRISE, "304")).isFalse();
	}

	@Test
	void a_proof_for_a_different_order_is_rejected_even_if_it_is_signed() {
		PaymentIntent intent = payments.start(SUNRISE, flat304.id(), OCTOBER, "Rajesh K");

		assertThatThrownBy(() -> payments.confirmCheckout(SUNRISE, intent.paymentId(), "order_someone_elses", "pay_1", "good-signature"))
				.isInstanceOf(InvalidSignatureException.class);

		assertThat(paid(SUNRISE, "304")).isFalse();
	}

	@Test
	void confirming_twice_from_the_browser_counts_once() {
		PaymentIntent intent = payments.start(SUNRISE, flat304.id(), OCTOBER, "Rajesh K");

		payments.confirmCheckout(SUNRISE, intent.paymentId(), intent.gatewayOrderId(), "pay_1", "good-signature");
		payments.confirmCheckout(SUNRISE, intent.paymentId(), intent.gatewayOrderId(), "pay_1", "good-signature");

		assertThat(admin.queryForObject("select count(*) from payments where status = 'CONFIRMED'", Integer.class)).isEqualTo(1);
	}

	@Test
	void a_payment_cannot_be_confirmed_through_another_society() {
		PaymentIntent intent = payments.start(SUNRISE, flat304.id(), OCTOBER, "Rajesh K");

		assertThatThrownBy(() -> payments.confirmCheckout(LAKEVIEW, intent.paymentId(), intent.gatewayOrderId(), "pay_1", "good-signature"))
				.isInstanceOf(FlatNotFoundException.class);

		assertThat(paid(SUNRISE, "304")).isFalse();
	}

	// --- the provider's own notification ---

	@Test
	void a_webhook_marks_the_flat_paid_without_being_told_which_society() {
		PaymentIntent intent = payments.start(SUNRISE, flat304.id(), OCTOBER, "Rajesh K");

		payments.confirmFromWebhook(new GatewayPayment(intent.gatewayOrderId(), "pay_1", intent.total(), "upi"));

		assertThat(paid(SUNRISE, "304")).isTrue();
		assertThat(paid(LAKEVIEW, "A-1")).isFalse();
	}

	@Test
	void a_webhook_after_the_browser_already_confirmed_counts_once() {
		PaymentIntent intent = payments.start(SUNRISE, flat304.id(), OCTOBER, "Rajesh K");
		payments.confirmCheckout(SUNRISE, intent.paymentId(), intent.gatewayOrderId(), "pay_1", "good-signature");

		payments.confirmFromWebhook(new GatewayPayment(intent.gatewayOrderId(), "pay_1", intent.total(), "upi"));

		assertThat(admin.queryForObject("select count(*) from payments where status = 'CONFIRMED'", Integer.class)).isEqualTo(1);
	}

	@Test
	void a_webhook_delivered_twice_counts_once() {
		PaymentIntent intent = payments.start(SUNRISE, flat304.id(), OCTOBER, "Rajesh K");
		GatewayPayment event = new GatewayPayment(intent.gatewayOrderId(), "pay_1", intent.total(), "upi");

		payments.confirmFromWebhook(event);
		payments.confirmFromWebhook(event);

		assertThat(admin.queryForObject("select count(*) from payments where status = 'CONFIRMED'", Integer.class)).isEqualTo(1);
	}

	@Test
	void a_webhook_for_an_order_we_do_not_know_is_ignored() {
		payments.confirmFromWebhook(new GatewayPayment("order_unknown", "pay_1", Money.rupees(1500), "upi"));

		assertThat(admin.queryForObject("select count(*) from payments where status = 'CONFIRMED'", Integer.class)).isZero();
	}

	@Test
	void a_webhook_with_the_wrong_amount_is_rejected_and_the_flat_stays_pending() {
		PaymentIntent intent = payments.start(SUNRISE, flat304.id(), OCTOBER, "Rajesh K");

		assertThatThrownBy(() -> payments.confirmFromWebhook(
				new GatewayPayment(intent.gatewayOrderId(), "pay_1", Money.rupees(1), "upi")))
				.isInstanceOf(AmountMismatchException.class);

		assertThat(paid(SUNRISE, "304")).isFalse();
	}
}
