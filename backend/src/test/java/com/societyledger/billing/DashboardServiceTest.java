package com.societyledger.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.societyledger.common.Money;
import com.societyledger.common.tenant.SocietyScope;
import com.societyledger.payment.OfflineMethod;
import com.societyledger.payment.OfflinePaymentCommand;
import com.societyledger.payment.OfflinePaymentService;
import com.societyledger.payment.PaymentIntent;
import com.societyledger.payment.PaymentService;
import com.societyledger.society.Flat;
import com.societyledger.society.FlatRepository;
import com.societyledger.support.DatabaseTest;
import com.societyledger.support.MutableClock;
import com.societyledger.support.PostgresTestSupport;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@DatabaseTest
class DashboardServiceTest {

	private static final UUID SUNRISE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private static final UUID LAKEVIEW = UUID.fromString("00000000-0000-0000-0000-00000000000b");
	private static final UUID TREASURER = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
	private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

	@Autowired
	private DashboardService dashboard;
	@Autowired
	private PaymentService online;
	@Autowired
	private OfflinePaymentService offline;
	@Autowired
	private SocietyScope scope;
	@Autowired
	private FlatRepository flats;
	@Autowired
	private MutableClock clock;

	private final JdbcTemplate admin = PostgresTestSupport.admin();
	private Flat flat301;
	private Flat flat302;
	private Flat flat303;

	@BeforeEach
	void threeFlatsOnAnEarlyOctoberMorning() {
		admin.execute("truncate audit_log, sessions, otp_challenges, members, payments, charges, flats, societies cascade");
		admin.update("insert into societies (id, code, name) values (?, 'sunrise', 'Sunrise Apartments')", SUNRISE);
		admin.update("insert into societies (id, code, name) values (?, 'lakeview', 'Lakeview Heights')", LAKEVIEW);
		flat301 = scope.inSociety(SUNRISE, () -> flats.add(SUNRISE, "301", 3, Money.rupees(1000)));
		flat302 = scope.inSociety(SUNRISE, () -> flats.add(SUNRISE, "302", 3, Money.rupees(1500)));
		flat303 = scope.inSociety(SUNRISE, () -> flats.add(SUNRISE, "303", 3, Money.rupees(2000)));
		admin.update("""
				insert into members (id, society_id, name, mobile, email, role)
				values (?, ?, 'Meena T', '9876500001', 'meena@example.com', 'TREASURER')""", TREASURER, SUNRISE);
		clock.set("2026-10-05T04:30:00Z");
	}

	private void payOnline(Flat flat, String reference) {
		PaymentIntent intent = online.start(SUNRISE, flat.id(), OCTOBER, "Someone");
		online.confirm(SUNRISE, intent.paymentId(), reference, "UPI", intent.total());
	}

	private void payCash(Flat flat, boolean waive, String reason) {
		offline.record(SUNRISE, TREASURER, "Meena T",
				new OfflinePaymentCommand(flat.id(), OCTOBER, OfflineMethod.CASH, null, waive, reason));
	}

	@Test
	void counts_paid_and_pending_flats() {
		payOnline(flat301, "pay_1");

		Dashboard result = dashboard.forMonth(SUNRISE, OCTOBER);

		assertThat(result.flatCount()).isEqualTo(3);
		assertThat(result.paidCount()).isEqualTo(1);
		assertThat(result.pendingCount()).isEqualTo(2);
	}

	@Test
	void expected_is_the_sum_of_every_flats_amount() {
		assertThat(dashboard.forMonth(SUNRISE, OCTOBER).expected()).isEqualTo(Money.rupees(4500));
	}

	@Test
	void collected_splits_online_from_offline() {
		payOnline(flat301, "pay_1");
		payCash(flat302, false, null);

		Dashboard result = dashboard.forMonth(SUNRISE, OCTOBER);

		assertThat(result.onlineCollected()).isEqualTo(Money.rupees(1000));
		assertThat(result.offlineCollected()).isEqualTo(Money.rupees(1500));
		assertThat(result.collected()).isEqualTo(Money.rupees(2500));
	}

	@Test
	void late_fees_collected_and_waived_are_totalled_separately() {
		clock.set("2026-10-20T10:00:00Z");
		payOnline(flat301, "pay_1");
		payCash(flat302, true, "Owner was in hospital");

		Dashboard result = dashboard.forMonth(SUNRISE, OCTOBER);

		assertThat(result.lateFeesCollected()).isEqualTo(Money.rupees(100));
		assertThat(result.lateFeesWaived()).isEqualTo(Money.rupees(100));
		assertThat(result.collected()).isEqualTo(Money.rupees(1100 + 1500));
	}

	@Test
	void pending_flats_show_what_is_due_right_now() {
		clock.set("2026-10-20T10:00:00Z");
		payOnline(flat301, "pay_1");

		Dashboard result = dashboard.forMonth(SUNRISE, OCTOBER);

		assertThat(result.pending()).extracting(PendingFlat::number).containsExactly("302", "303");
		assertThat(result.pending()).extracting(PendingFlat::dueNow)
				.containsExactly(Money.rupees(1600), Money.rupees(2100));
	}

	@Test
	void pending_flats_carry_their_id_and_their_amount_before_any_late_fee() {
		clock.set("2026-10-20T10:00:00Z");

		Dashboard result = dashboard.forMonth(SUNRISE, OCTOBER);

		PendingFlat first = result.pending().get(0);
		assertThat(first.flatId()).isEqualTo(flat301.id());
		assertThat(first.amount()).isEqualTo(Money.rupees(1000));
		assertThat(first.dueNow()).isEqualTo(Money.rupees(1100));
	}

	@Test
	void another_societys_money_is_not_counted() {
		Flat other = scope.inSociety(LAKEVIEW, () -> flats.add(LAKEVIEW, "A-1", 1, Money.rupees(5000)));

		Dashboard result = dashboard.forMonth(SUNRISE, OCTOBER);

		assertThat(result.flatCount()).isEqualTo(3);
		assertThat(result.expected()).isEqualTo(Money.rupees(4500));
		assertThat(other).isNotNull();
	}
}
