package com.societyledger.payment;

import com.societyledger.common.Money;
import java.util.Optional;
import java.util.UUID;

/** What the application needs from an online payment provider. Razorpay today; another can replace it. */
public interface PaymentGateway {

	/** Creates the order the customer pays against. Our payment id is the provider's receipt. */
	GatewayOrder createOrder(UUID paymentId, Money amount);

	/** Whether the proof of payment handed back to the browser really came from the provider. */
	boolean isValidCheckoutSignature(String orderId, String gatewayPaymentId, String signature);

	/**
	 * Verifies and reads a notification from the provider. Empty when it is not a payment we act on.
	 *
	 * @throws InvalidWebhookException when the signature is wrong or the body cannot be read
	 */
	Optional<GatewayPayment> readWebhook(byte[] body, String signature);

	/** The public key the browser needs to open the payment page. Never a secret. */
	String checkoutKey();
}
