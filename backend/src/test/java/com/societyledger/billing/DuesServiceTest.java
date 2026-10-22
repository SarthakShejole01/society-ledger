package com.societyledger.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.societyledger.common.Money;
import com.societyledger.common.tenant.SocietyScope;
import com.societyledger.payment.FlatNotFoundException;
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
class DuesServiceTest {

	private static final UUID SUNRISE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private static final UUID LAKEVIEW = UUID.fromString("00000000-0000-0000-0000-00000000000b");
	private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

	@Autowired
	private DuesService dues;
	@Autowired
	private PaymentService payments;
	@Autowired
	private SocietyScope scope;
	@Autowired
	private FlatRepository flats;
	@Autowired
	private MutableClock clock;

	private final JdbcTemplate admin = PostgresTestSupport.admin();
	private Flat flat304;

	@BeforeEach
	void flat304PaysFifteenHundred() {
		admin.execute("truncate sessions, otp_challenges, members, payments, charges, flats, societies cascade");
		admin.update("insert into societies (id, code, name) values (?, 'sunrise', 'Sunrise Apartments')", SUNRISE);
		admin.update("insert into societies (id, code, name) values (?, 'lakeview', 'Lakeview Heights')", LAKEVIEW);
		flat304 = scope.inSociety(SUNRISE, () -> flats.add(SUNRISE, "304", 3, Money.rupees(1500)));
		clock.set("2026-10-05T04:30:00Z");
	}

	@Test
	void before_the_eleventh_the_dues_are_the_flats_amount() {
		Dues result = dues.forFlat(SUNRISE, flat304.id(), OCTOBER);

		assertThat(result.flatNumber()).isEqualTo("304");
		assertThat(result.amount()).isEqualTo(Money.rupees(1500));
		assertThat(result.lateFee()).isEqualTo(Money.ZERO);
		assertThat(result.total()).isEqualTo(Money.rupees(1500));
		assertThat(result.paid()).isFalse();
	}

	@Test
	void after_the_tenth_the_dues_include_the_late_fee() {
		clock.set("2026-10-20T10:00:00Z");

		Dues result = dues.forFlat(SUNRISE, flat304.id(), OCTOBER);

		assertThat(result.lateFee()).isEqualTo(Money.rupees(100));
		assertThat(result.total()).isEqualTo(Money.rupees(1600));
	}

	@Test
	void asking_for_the_dues_does_not_create_anything() {
		dues.forFlat(SUNRISE, flat304.id(), OCTOBER);

		assertThat(admin.queryForObject("select count(*) from charges", Integer.class)).isZero();
		assertThat(admin.queryForObject("select count(*) from payments", Integer.class)).isZero();
	}

	@Test
	void a_paid_month_owes_nothing() {
		PaymentIntent intent = payments.start(SUNRISE, flat304.id(), OCTOBER, "Rajesh K");
		payments.confirm(SUNRISE, intent.paymentId(), "pay_1", "UPI", intent.total());
		clock.set("2026-10-25T10:00:00Z");

		Dues result = dues.forFlat(SUNRISE, flat304.id(), OCTOBER);

		assertThat(result.paid()).isTrue();
		assertThat(result.total()).isEqualTo(Money.ZERO);
		assertThat(result.lateFee()).isEqualTo(Money.ZERO);
	}

	@Test
	void another_societys_flat_is_not_found() {
		Flat other = scope.inSociety(LAKEVIEW, () -> flats.add(LAKEVIEW, "A-1", 1, Money.rupees(2000)));

		assertThatThrownBy(() -> dues.forFlat(SUNRISE, other.id(), OCTOBER))
				.isInstanceOf(FlatNotFoundException.class);
	}
}
