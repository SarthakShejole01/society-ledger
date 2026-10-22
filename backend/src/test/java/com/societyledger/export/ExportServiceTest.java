package com.societyledger.export;

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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@DatabaseTest
class ExportServiceTest {

	private static final UUID SUNRISE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private static final UUID LAKEVIEW = UUID.fromString("00000000-0000-0000-0000-00000000000b");
	private static final UUID TREASURER = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
	private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

	@Autowired
	private ExportService export;
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

	private List<String> lines() {
		String csv = export.monthCsv(SUNRISE, TREASURER, OCTOBER);
		return List.of(csv.substring(1).split("\r\n"));
	}

	private void payOnline(Flat flat, String payer, String reference) {
		PaymentIntent intent = online.start(SUNRISE, flat.id(), OCTOBER, payer);
		online.confirm(SUNRISE, intent.paymentId(), reference, "upi", intent.total());
	}

	@Test
	void starts_with_a_byte_order_mark_so_spreadsheets_read_the_text_correctly() {
		assertThat(export.monthCsv(SUNRISE, TREASURER, OCTOBER)).startsWith("﻿");
	}

	@Test
	void has_a_header_and_a_row_for_every_flat_in_floor_and_number_order() {
		List<String> rows = lines();

		assertThat(rows.get(0)).isEqualTo("Flat,Floor,Status,Maintenance,Late fee charged,Late fee waived,Total paid,Channel,Method,Paid on,Paid by,Reference,Waiver reason");
		assertThat(rows).hasSize(4);
		assertThat(rows.get(1)).startsWith("301,3,Pending");
		assertThat(rows.get(3)).startsWith("303,3,Pending");
	}

	@Test
	void a_pending_flat_shows_its_maintenance_and_nothing_paid() {
		assertThat(lines().get(2)).isEqualTo("302,3,Pending,1500.00,,,,,,,,,");
	}

	@Test
	void an_online_payment_shows_what_was_paid_by_whom_and_the_gateway_reference() {
		clock.set("2026-10-20T10:00:00Z");
		payOnline(flat301, "Amit S", "pay_ABC123");

		assertThat(lines().get(1)).isEqualTo("301,3,Paid,1000.00,100.00,0.00,1100.00,ONLINE,upi,2026-10-20 15:30,Amit S,pay_ABC123,");
	}

	@Test
	void a_cash_payment_with_a_waived_late_fee_shows_the_waiver_and_its_reason() {
		clock.set("2026-10-20T10:00:00Z");
		offline.record(SUNRISE, TREASURER, "Meena T",
				new OfflinePaymentCommand(flat302.id(), OCTOBER, OfflineMethod.CASH, null, true, "Owner was in hospital"));

		assertThat(lines().get(2)).isEqualTo("302,3,Paid,1500.00,0.00,100.00,1500.00,OFFLINE,CASH,2026-10-20 15:30,Cash recorded by Meena T,,Owner was in hospital");
	}

	@Test
	void a_cheque_number_is_included() {
		offline.record(SUNRISE, TREASURER, "Meena T",
				new OfflinePaymentCommand(flat303.id(), OCTOBER, OfflineMethod.CHEQUE, "000123", false, null));

		assertThat(lines().get(3)).contains(",CHEQUE,").contains(",000123,");
	}

	@Test
	void a_payer_name_that_looks_like_a_formula_is_neutralised() {
		payOnline(flat301, "=HYPERLINK(\"http://evil\")", "pay_1");

		assertThat(lines().get(1)).contains("'=HYPERLINK").doesNotContain(",=HYPERLINK");
	}

	@Test
	void a_payer_name_with_a_comma_stays_in_one_cell() {
		payOnline(flat301, "Rao, Asha", "pay_1");

		assertThat(lines().get(1)).contains("\"Rao, Asha\"");
	}

	@Test
	void does_not_include_another_societys_flats() {
		scope.inSociety(LAKEVIEW, () -> flats.add(LAKEVIEW, "A-1", 1, Money.rupees(5000)));

		assertThat(export.monthCsv(SUNRISE, TREASURER, OCTOBER)).doesNotContain("A-1").doesNotContain("5000.00");
	}

	@Test
	void downloading_the_export_is_written_to_the_audit_log() {
		export.monthCsv(SUNRISE, TREASURER, OCTOBER);

		assertThat(admin.queryForObject("select action from audit_log", String.class)).isEqualTo("EXPORT_DOWNLOADED");
		assertThat(admin.queryForObject("select detail from audit_log", String.class)).contains("2026-10");
	}
}
