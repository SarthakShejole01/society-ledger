package com.societyledger.payment;

import com.societyledger.common.Money;
import java.time.YearMonth;
import java.util.UUID;

/** What the owner must pay, fixed when the payment is started, and what the browser needs to pay it. */
public record PaymentIntent(UUID paymentId, UUID flatId, YearMonth month, Money total, Money lateFee,
		String gatewayOrderId, String checkoutKey) {
}
