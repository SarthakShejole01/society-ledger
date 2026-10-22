package com.societyledger.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.societyledger.common.Money;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FakeGatewayTest {

	private final FakeGateway gateway = new FakeGateway();

	@Test
	void makes_an_order_that_names_the_payment() {
		UUID paymentId = UUID.randomUUID();

		assertThat(gateway.createOrder(paymentId, Money.rupees(1500)).orderId()).isEqualTo("order_fake_" + paymentId);
	}

	@Test
	void accepts_only_its_own_made_up_signature() {
		assertThat(gateway.isValidCheckoutSignature("order_fake_1", "pay_fake_1", "fake-signature")).isTrue();
		assertThat(gateway.isValidCheckoutSignature("order_fake_1", "pay_fake_1", "anything-else")).isFalse();
		assertThat(gateway.isValidCheckoutSignature("order_fake_1", "pay_fake_1", null)).isFalse();
	}

	@Test
	void trusts_no_webhooks() {
		assertThatThrownBy(() -> gateway.readWebhook(new byte[0], "x")).isInstanceOf(InvalidWebhookException.class);
	}

	@Test
	void its_checkout_key_tells_the_web_app_to_simulate_the_payment_page() {
		assertThat(gateway.checkoutKey()).isEqualTo("fake");
	}
}
