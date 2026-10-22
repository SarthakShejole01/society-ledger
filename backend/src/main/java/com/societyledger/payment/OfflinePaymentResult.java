package com.societyledger.payment;

import com.societyledger.common.Money;
import java.util.UUID;

public record OfflinePaymentResult(UUID paymentId, Money total, Money lateFee, Money waived) {
}
