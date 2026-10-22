package com.societyledger.payment;

import java.time.YearMonth;
import java.util.UUID;

/** A cash or cheque payment the treasurer received. The amount is never part of it: the server works it out. */
public record OfflinePaymentCommand(UUID flatId, YearMonth month, OfflineMethod method, String reference,
		boolean waiveLateFee, String waiverReason) {
}
