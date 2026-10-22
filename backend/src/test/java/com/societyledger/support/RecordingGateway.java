package com.societyledger.support;

import com.societyledger.common.Money;
import com.societyledger.payment.GatewayOrder;
import com.societyledger.payment.GatewayPayment;
import com.societyledger.payment.GatewayUnavailableException;
import com.societyledger.payment.InvalidWebhookException;
import com.societyledger.payment.PaymentGateway;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** A gateway for tests: remembers the orders it was asked for and answers as told. */
public class RecordingGateway implements PaymentGateway {

	public record Order(UUID paymentId, Money amount, String orderId) {
	}

	private final List<Order> orders = new ArrayList<>();
	private boolean signaturesAreValid = true;
	private boolean down;
	private Optional<GatewayPayment> webhook = Optional.empty();
	private boolean webhookIsForged;
	private int counter;

	@Override
	public synchronized GatewayOrder createOrder(UUID paymentId, Money amount) {
		if (down) {
			throw new GatewayUnavailableException(new IllegalStateException("gateway down"));
		}
		String orderId = "order_test_" + (++counter);
		orders.add(new Order(paymentId, amount, orderId));
		return new GatewayOrder(orderId);
	}

	@Override
	public synchronized boolean isValidCheckoutSignature(String orderId, String gatewayPaymentId, String signature) {
		return signaturesAreValid && "good-signature".equals(signature);
	}

	@Override
	public synchronized Optional<GatewayPayment> readWebhook(byte[] body, String signature) {
		if (webhookIsForged) {
			throw new InvalidWebhookException();
		}
		return webhook;
	}

	@Override
	public String checkoutKey() {
		return "key_test";
	}

	public synchronized List<Order> orders() {
		return List.copyOf(orders);
	}

	public synchronized void goDown() {
		this.down = true;
	}

	public synchronized void webhookCarries(GatewayPayment payment) {
		this.webhook = Optional.of(payment);
	}

	public synchronized void webhookIsForged() {
		this.webhookIsForged = true;
	}

	public synchronized void reset() {
		orders.clear();
		down = false;
		webhook = Optional.empty();
		webhookIsForged = false;
		signaturesAreValid = true;
		counter = 0;
	}
}
