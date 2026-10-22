package com.societyledger.payment;

import com.societyledger.common.Money;
import java.time.YearMonth;
import java.util.UUID;

/** What the owner must pay, fixed when the payment is started. */
public record PaymentIntent(UUID paymentId, UUID flatId, YearMonth month, Money total, Money lateFee) {
}
