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
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@DatabaseTest
class OfflinePaymentServiceTest {

	private static final UUID SUNRISE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private static final UUID LAKEVIEW = UUID.fromString("00000000-0000-0000-0000-00000000000b");
	private static final UUID TREASURER = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
	private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

	@Autowired
	private OfflinePaymentService offline;
	@Autowired
	private PaymentService online;
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

	@BeforeEach
	void flat304IsUnpaidOnAnEarlyOctoberMorning() {
		admin.execute("truncate audit_log, sessions, otp_challenges, members, payments, charges, flats, societies cascade");
		admin.update("insert into societies (id, code, name) values (?, 'sunrise', 'Sunrise Apartments')", SUNRISE);
		admin.update("insert into societies (id, code, name) values (?, 'lakeview', 'Lakeview Heights')", LAKEVIEW);
		flat304 = scope.inSociety(SUNRISE, () -> flats.add(SUNRISE, "304", 3, Money.rupees(1500)));
		admin.update("""
				insert into members (id, society_id, name, mobile, email, role)
				values (?, ?, 'Meena T', '9876500001', 'meena@example.com', 'TREASURER')""", TREASURER, SUNRISE);
		clock.set("2026-10-05T04:30:00Z");
	}

	private OfflinePaymentResult record(OfflineMethod method, String reference, boolean waive, String reason) {
		return offline.record(SUNRISE, TREASURER, "Meena T",
				new OfflinePaymentCommand(flat304.id(), OCTOBER, method, reference, waive, reason));
	}

	private boolean paid() {
		List<FlatStatus> statuses = board.forMonth(SUNRISE, OCTOBER);
		return statuses.get(0).paid();
	}

	@Test
	void recording_cash_marks_the_flat_paid() {
		OfflinePaymentResult result = record(OfflineMethod.CASH, null, false, null);

		assertThat(result.total()).isEqualTo(Money.rupees(1500));
		assertThat(paid()).isTrue();
	}

	@Test
	void an_offline_payment_after_the_tenth_includes_the_late_fee() {
		clock.set("2026-10-20T10:00:00Z");

		OfflinePaymentResult result = record(OfflineMethod.CASH, null, false, null);

		assertThat(result.total()).isEqualTo(Money.rupees(1600));
		assertThat(result.lateFee()).isEqualTo(Money.rupees(100));
		assertThat(result.waived()).isEqualTo(Money.ZERO);
	}

	@Test
	void a_waived_late_fee_is_not_charged_and_the_reason_is_kept() {
		clock.set("2026-10-20T10:00:00Z");

		OfflinePaymentResult result = record(OfflineMethod.CASH, null, true, "Owner was in hospital");

		assertThat(result.total()).isEqualTo(Money.rupees(1500));
		assertThat(result.lateFee()).isEqualTo(Money.ZERO);
		assertThat(result.waived()).isEqualTo(Money.rupees(100));
		Map<String, Object> row = admin.queryForMap("select waived_late_fee_paise, waiver_reason from payments");
		assertThat(row.get("waived_late_fee_paise")).isEqualTo(10_000L);
		assertThat(row.get("waiver_reason")).isEqualTo("Owner was in hospital");
	}

	@Test
	void waiving_needs_a_reason_and_records_nothing_without_one() {
		clock.set("2026-10-20T10:00:00Z");

		assertThatThrownBy(() -> record(OfflineMethod.CASH, null, true, "  "))
				.isInstanceOf(WaiverReasonRequiredException.class);

		assertThat(paid()).isFalse();
		assertThat(admin.queryForObject("select count(*) from payments", Integer.class)).isZero();
	}

	@Test
	void waiving_when_there_is_no_late_fee_records_no_waiver() {
		OfflinePaymentResult result = record(OfflineMethod.CASH, null, true, "Not needed");

		assertThat(result.waived()).isEqualTo(Money.ZERO);
		assertThat(admin.queryForObject("select waiver_reason from payments", String.class)).isNull();
	}

	@Test
	void a_cheque_number_is_kept() {
		record(OfflineMethod.CHEQUE, "000123", false, null);

		Map<String, Object> row = admin.queryForMap("select method, channel, reference_note, recorded_by from payments");
		assertThat(row.get("method")).isEqualTo("CHEQUE");
		assertThat(row.get("channel")).isEqualTo("OFFLINE");
		assertThat(row.get("reference_note")).isEqualTo("000123");
		assertThat(row.get("recorded_by")).isEqualTo(TREASURER);
	}

	@Test
	void a_month_already_paid_cannot_be_recorded_again() {
		record(OfflineMethod.CASH, null, false, null);

		assertThatThrownBy(() -> record(OfflineMethod.CASH, null, false, null))
				.isInstanceOf(AlreadyPaidException.class);
	}

	@Test
	void a_month_paid_online_cannot_also_be_recorded_as_cash() {
		PaymentIntent intent = online.start(SUNRISE, flat304.id(), OCTOBER, "Rajesh K");
		online.confirm(SUNRISE, intent.paymentId(), "pay_1", "UPI", intent.total());

		assertThatThrownBy(() -> record(OfflineMethod.CASH, null, false, null))
				.isInstanceOf(AlreadyPaidException.class);
	}

	@Test
	void recording_a_payment_writes_an_audit_entry() {
		record(OfflineMethod.CASH, null, false, null);

		Map<String, Object> entry = admin.queryForMap("select action, detail, member_id from audit_log");
		assertThat(entry.get("action")).isEqualTo("PAYMENT_RECORDED");
		assertThat(entry.get("member_id")).isEqualTo(TREASURER);
		assertThat((String) entry.get("detail")).contains("304").contains("CASH");
	}

	@Test
	void waiving_a_late_fee_writes_its_own_audit_entry_with_the_reason() {
		clock.set("2026-10-20T10:00:00Z");

		record(OfflineMethod.CASH, null, true, "Owner was in hospital");

		List<String> actions = admin.queryForList("select action from audit_log order by action", String.class);
		assertThat(actions).containsExactly("LATE_FEE_WAIVED", "PAYMENT_RECORDED");
		assertThat(admin.queryForObject("select detail from audit_log where action = 'LATE_FEE_WAIVED'", String.class))
				.contains("Owner was in hospital");
	}

	@Test
	void another_societys_flat_is_not_found() {
		Flat other = scope.inSociety(LAKEVIEW, () -> flats.add(LAKEVIEW, "A-1", 1, Money.rupees(2000)));

		assertThatThrownBy(() -> offline.record(SUNRISE, TREASURER, "Meena T",
				new OfflinePaymentCommand(other.id(), OCTOBER, OfflineMethod.CASH, null, false, null)))
				.isInstanceOf(FlatNotFoundException.class);
	}
}
