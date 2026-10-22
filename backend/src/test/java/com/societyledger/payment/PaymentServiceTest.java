package com.societyledger.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.societyledger.billing.FlatStatus;
import com.societyledger.billing.StatusBoardService;
import com.societyledger.common.Money;
import com.societyledger.common.tenant.SocietyScope;
import com.societyledger.society.Flat;
import com.societyledger.society.FlatRepository;
import com.societyledger.support.DatabaseTest;
import com.societyledger.support.MutableClock;
import com.societyledger.support.PostgresTestSupport;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@DatabaseTest
class PaymentServiceTest {

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

	private final JdbcTemplate admin = PostgresTestSupport.admin();
	private Flat flat304;
	private Flat flat305;

	@BeforeEach
	void aSocietyWithTwoFlatsAndAnEarlyOctoberMorning() {
		admin.execute("truncate payments, charges, flats, societies cascade");
		admin.update("insert into societies (id, code, name) values (?, 'sunrise', 'Sunrise Apartments')", SUNRISE);
		admin.update("insert into societies (id, code, name) values (?, 'lakeview', 'Lakeview Heights')", LAKEVIEW);
		flat304 = scope.inSociety(SUNRISE, () -> flats.add(SUNRISE, "304", 3, Money.rupees(1500)));
		flat305 = scope.inSociety(SUNRISE, () -> flats.add(SUNRISE, "305", 3, Money.rupees(1200)));
		clock.set("2026-10-05T04:30:00Z"); // 10:00 IST on 5 October
	}

	@Test
	void paying_on_time_asks_for_the_flats_amount_only() {
		PaymentIntent intent = payments.start(SUNRISE, flat304.id(), OCTOBER, "Rajesh K");

		assertThat(intent.total()).isEqualTo(Money.rupees(1500));
		assertThat(intent.lateFee()).isEqualTo(Money.ZERO);
	}

	@Test
	void paying_after_the_tenth_adds_the_late_fee() {
		clock.set("2026-10-20T10:00:00Z");

		PaymentIntent intent = payments.start(SUNRISE, flat304.id(), OCTOBER, "Rajesh K");

		assertThat(intent.total()).isEqualTo(Money.rupees(1600));
		assertThat(intent.lateFee()).isEqualTo(Money.rupees(100));
	}

	@Test
	void the_late_fee_follows_when_the_payment_was_started_not_when_it_was_confirmed() {
		clock.set("2026-10-10T18:29:00Z"); // 23:59 IST on the 10th
		PaymentIntent intent = payments.start(SUNRISE, flat304.id(), OCTOBER, "Rajesh K");
		clock.set("2026-10-10T18:40:00Z"); // 00:10 IST on the 11th, a slow bank

		payments.confirm(SUNRISE, intent.paymentId(), "pay_slow", "UPI", Money.rupees(1500));

		assertThat(statusOf("304")).isTrue();
	}

	@Test
	void a_confirmed_payment_marks_the_flat_paid_for_the_month() {
		PaymentIntent intent = payments.start(SUNRISE, flat304.id(), OCTOBER, "Rajesh K");

		payments.confirm(SUNRISE, intent.paymentId(), "pay_1", "UPI", intent.total());

		assertThat(statusOf("304")).isTrue();
		assertThat(statusOf("305")).isFalse();
	}

	@Test
	void an_unconfirmed_payment_leaves_the_flat_pending() {
		payments.start(SUNRISE, flat304.id(), OCTOBER, "Rajesh K");

		assertThat(statusOf("304")).isFalse();
	}

	@Test
	void confirming_the_same_gateway_reference_twice_counts_once() {
		PaymentIntent intent = payments.start(SUNRISE, flat304.id(), OCTOBER, "Rajesh K");

		payments.confirm(SUNRISE, intent.paymentId(), "pay_1", "UPI", intent.total());
		payments.confirm(SUNRISE, intent.paymentId(), "pay_1", "UPI", intent.total());

		assertThat(admin.queryForObject("select count(*) from payments where status = 'CONFIRMED'", Integer.class))
				.isEqualTo(1);
	}

	@Test
	void a_confirmation_with_the_wrong_amount_is_rejected_and_the_flat_stays_pending() {
		PaymentIntent intent = payments.start(SUNRISE, flat304.id(), OCTOBER, "Rajesh K");

		assertThatThrownBy(() -> payments.confirm(SUNRISE, intent.paymentId(), "pay_1", "UPI", Money.rupees(1000)))
				.isInstanceOf(AmountMismatchException.class);

		assertThat(statusOf("304")).isFalse();
	}

	@Test
	void a_month_that_is_already_paid_cannot_be_paid_again() {
		PaymentIntent intent = payments.start(SUNRISE, flat304.id(), OCTOBER, "Rajesh K");
		payments.confirm(SUNRISE, intent.paymentId(), "pay_1", "UPI", intent.total());

		assertThatThrownBy(() -> payments.start(SUNRISE, flat304.id(), OCTOBER, "Rajesh K"))
				.isInstanceOf(AlreadyPaidException.class);
	}

	@Test
	void anyone_can_pay_for_another_flat_and_the_payer_is_recorded() {
		PaymentIntent intent = payments.start(SUNRISE, flat305.id(), OCTOBER, "Amit S (flat 304)");

		assertThat(intent.flatId()).isEqualTo(flat305.id());
		assertThat(admin.queryForObject("select payer_name from payments", String.class)).isEqualTo("Amit S (flat 304)");
	}

	@Test
	void a_society_cannot_pay_for_another_societys_flat() {
		Flat other = scope.inSociety(LAKEVIEW, () -> flats.add(LAKEVIEW, "A-1", 1, Money.rupees(2000)));

		assertThatThrownBy(() -> payments.start(SUNRISE, other.id(), OCTOBER, "Rajesh K"))
				.isInstanceOf(FlatNotFoundException.class);
	}

	@Test
	void a_gateway_reference_cannot_be_reused_for_a_different_payment() {
		PaymentIntent first = payments.start(SUNRISE, flat304.id(), OCTOBER, "Rajesh K");
		PaymentIntent second = payments.start(SUNRISE, flat305.id(), OCTOBER, "Rajesh K");
		payments.confirm(SUNRISE, first.paymentId(), "pay_1", "UPI", first.total());

		assertThatThrownBy(() -> payments.confirm(SUNRISE, second.paymentId(), "pay_1", "UPI", second.total()))
				.isInstanceOf(GatewayReferenceReusedException.class);

		assertThat(statusOf("305")).isFalse();
	}

	private boolean statusOf(String flatNumber) {
		List<FlatStatus> statuses = board.forMonth(SUNRISE, OCTOBER);
		return statuses.stream().filter(s -> s.number().equals(flatNumber)).findFirst().orElseThrow().paid();
	}
}
