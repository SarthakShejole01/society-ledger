package com.societyledger.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.societyledger.common.Money;
import java.time.Instant;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class LateFeePolicyTest {

	private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
	private final LateFeePolicy policy = LateFeePolicy.of(Money.rupees(100));

	@Test
	void no_late_fee_on_the_first_of_the_month() {
		Instant startedAt = Instant.parse("2026-10-01T04:00:00Z");

		assertThat(policy.feeFor(OCTOBER, startedAt)).isEqualTo(Money.ZERO);
	}

	@Test
	void no_late_fee_in_the_last_second_of_the_tenth_in_india() {
		// 23:59:59 IST on 10 October is 18:29:59 UTC
		Instant startedAt = Instant.parse("2026-10-10T18:29:59Z");

		assertThat(policy.feeFor(OCTOBER, startedAt)).isEqualTo(Money.ZERO);
	}

	@Test
	void late_fee_applies_from_midnight_of_the_eleventh_in_india() {
		// 00:00:00 IST on 11 October is 18:30:00 UTC on the 10th
		Instant startedAt = Instant.parse("2026-10-10T18:30:00Z");

		assertThat(policy.feeFor(OCTOBER, startedAt)).isEqualTo(Money.rupees(100));
	}

	@Test
	void late_fee_applies_for_the_rest_of_the_month() {
		Instant startedAt = Instant.parse("2026-10-31T10:00:00Z");

		assertThat(policy.feeFor(OCTOBER, startedAt)).isEqualTo(Money.rupees(100));
	}

	@Test
	void no_late_fee_for_a_month_that_has_not_started() {
		Instant startedAt = Instant.parse("2026-10-20T10:00:00Z");

		assertThat(policy.feeFor(YearMonth.of(2026, 11), startedAt)).isEqualTo(Money.ZERO);
	}

	@Test
	void late_fee_applies_to_an_earlier_unpaid_month() {
		Instant startedAt = Instant.parse("2026-11-03T10:00:00Z");

		assertThat(policy.feeFor(OCTOBER, startedAt)).isEqualTo(Money.rupees(100));
	}
}
