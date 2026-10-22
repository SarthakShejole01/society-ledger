package com.societyledger.billing;

import com.societyledger.common.Money;
import java.time.YearMonth;
import java.util.List;

/**
 * A month at a glance for the treasurer and committee. Collected money includes late fees and is split by
 * how it arrived, so it can be matched against the bank statement and the gateway's report.
 */
public record Dashboard(YearMonth month, int flatCount, int paidCount, int pendingCount, Money expected, Money collected,
		Money onlineCollected, Money offlineCollected, Money lateFeesCollected, Money lateFeesWaived,
		List<PendingFlat> pending) {
}
