package com.societyledger.payment;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where the payment provider reports payments. There is no login here: the provider's signature over the exact
 * bytes it sent is the only proof, and without a valid one nothing happens.
 */
@RestController
class GatewayWebhookController {

	private static final Logger log = LoggerFactory.getLogger(GatewayWebhookController.class);

	private final PaymentGateway gateway;
	private final PaymentService payments;

	GatewayWebhookController(PaymentGateway gateway, PaymentService payments) {
		this.gateway = gateway;
		this.payments = payments;
	}

	@PostMapping("/api/gateway/webhook")
	ResponseEntity<Void> receive(@RequestBody byte[] body,
			@RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
		Optional<GatewayPayment> payment = gateway.readWebhook(body, signature);
		payment.ifPresent(this::confirm);
		return ResponseEntity.ok().build();
	}

	private void confirm(GatewayPayment payment) {
		try {
			payments.confirmFromWebhook(payment);
		}
		catch (AmountMismatchException | GatewayReferenceReusedException e) {
			// Something is wrong that retrying cannot fix, so the provider is told it was received.
			// The payment stays pending for the treasurer to look at.
			log.error("Payment {} for order {} was rejected: {}", payment.gatewayPaymentId(), payment.orderId(), e.getMessage());
		}
	}
}
