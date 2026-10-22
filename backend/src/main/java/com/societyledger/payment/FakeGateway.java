package com.societyledger.payment;

import com.societyledger.common.Money;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Local development only: pretends to be a payment provider so the whole flow can be tried without keys.
 * Anyone who knows the made-up signature can "pay", so it is active only under the "dev" profile and only
 * while no real provider is configured. Never enable it where real members pay.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "society-ledger.gateway", name = "provider", havingValue = "fake", matchIfMissing = true)
class FakeGateway implements PaymentGateway {

	static final String SIGNATURE = "fake-signature";

	@Override
	public GatewayOrder createOrder(UUID paymentId, Money amount) {
		return new GatewayOrder("order_fake_" + paymentId);
	}

	@Override
	public boolean isValidCheckoutSignature(String orderId, String gatewayPaymentId, String signature) {
		return SIGNATURE.equals(signature);
	}

	@Override
	public Optional<GatewayPayment> readWebhook(byte[] body, String signature) {
		throw new InvalidWebhookException();
	}

	@Override
	public String checkoutKey() {
		return "fake";
	}
}
