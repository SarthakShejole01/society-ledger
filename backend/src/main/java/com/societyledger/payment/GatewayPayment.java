package com.societyledger.payment;

import com.societyledger.common.Money;

/** A successful payment the provider tells us about. */
public record GatewayPayment(String orderId, String gatewayPaymentId, Money amount, String method) {
}
