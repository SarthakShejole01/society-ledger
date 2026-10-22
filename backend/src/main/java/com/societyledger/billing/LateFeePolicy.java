package com.societyledger.billing;

import com.societyledger.common.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * Decides the late fee for a month's maintenance charge.
 *
 * <p>Payment is free of the fee up to and including the last free day of the charge's month,
 * judged in Indian time from the moment the payment was started.
 */
public record LateFeePolicy(Money fee, int lastFreeDay, ZoneId zone) {

	private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
	private static final int DEFAULT_LAST_FREE_DAY = 10;

	public static LateFeePolicy of(Money fee) {
		return new LateFeePolicy(fee, DEFAULT_LAST_FREE_DAY, INDIA);
	}

	public Money feeFor(YearMonth chargeMonth, Instant paymentStartedAt) {
		LocalDate startedOn = paymentStartedAt.atZone(zone).toLocalDate();
		LocalDate lastFreeDate = chargeMonth.atDay(lastFreeDay);
		return startedOn.isAfter(lastFreeDate) ? fee : Money.ZERO;
	}
}
