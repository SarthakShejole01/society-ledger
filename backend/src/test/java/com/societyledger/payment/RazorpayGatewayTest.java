package com.societyledger.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.societyledger.common.Money;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The Razorpay client against a local stand-in for Razorpay's server, and its signature rules. */
class RazorpayGatewayTest {

	private static final String KEY_ID = "rzp_test_abc";
	private static final String KEY_SECRET = "key-secret-123";
	private static final String WEBHOOK_SECRET = "webhook-secret-456";

	private HttpServer server;
	private int status;
	private String responseBody;
	private final AtomicReference<String> requestLine = new AtomicReference<>();
	private final AtomicReference<String> authorization = new AtomicReference<>();
	private final AtomicReference<String> requestBody = new AtomicReference<>();
	private RazorpayGateway gateway;

	@BeforeEach
	void aStandInForRazorpay() throws IOException {
		status = 200;
		responseBody = "{\"id\":\"order_Abc123\",\"entity\":\"order\",\"amount\":160000,\"currency\":\"INR\",\"status\":\"created\"}";
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			requestLine.set(exchange.getRequestMethod() + " " + exchange.getRequestURI());
			authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
			requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		server.start();
		gateway = new RazorpayGateway("http://127.0.0.1:" + server.getAddress().getPort(), KEY_ID, KEY_SECRET, WEBHOOK_SECRET);
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	private static String hmac(String secret, byte[] data) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return HexFormat.of().formatHex(mac.doFinal(data));
	}

	// --- creating an order ---

	@Test
	void creates_an_order_for_the_amount_in_paise_with_our_payment_as_the_receipt() {
		UUID paymentId = UUID.randomUUID();

		GatewayOrder order = gateway.createOrder(paymentId, Money.rupees(1600));

		assertThat(order.orderId()).isEqualTo("order_Abc123");
		assertThat(requestLine.get()).isEqualTo("POST /v1/orders");
		assertThat(requestBody.get()).contains("\"amount\":160000").contains("\"currency\":\"INR\"")
				.contains("\"receipt\":\"" + paymentId + "\"");
	}

	@Test
	void signs_the_request_with_the_key_pair() {
		gateway.createOrder(UUID.randomUUID(), Money.rupees(1600));

		String expected = "Basic " + Base64.getEncoder().encodeToString((KEY_ID + ":" + KEY_SECRET).getBytes(StandardCharsets.UTF_8));
		assertThat(authorization.get()).isEqualTo(expected);
	}

	@Test
	void a_gateway_error_is_reported_as_unavailable() {
		status = 500;
		responseBody = "{\"error\":{\"description\":\"boom\"}}";

		assertThatThrownBy(() -> gateway.createOrder(UUID.randomUUID(), Money.rupees(1600)))
				.isInstanceOf(GatewayUnavailableException.class);
	}

	@Test
	void a_rejected_request_is_reported_as_unavailable() {
		status = 400;
		responseBody = "{\"error\":{\"description\":\"bad\"}}";

		assertThatThrownBy(() -> gateway.createOrder(UUID.randomUUID(), Money.rupees(1600)))
				.isInstanceOf(GatewayUnavailableException.class);
	}

	@Test
	void a_reply_without_an_order_id_is_reported_as_unavailable() {
		responseBody = "{\"entity\":\"order\"}";

		assertThatThrownBy(() -> gateway.createOrder(UUID.randomUUID(), Money.rupees(1600)))
				.isInstanceOf(GatewayUnavailableException.class);
	}

	@Test
	void an_unreachable_gateway_is_reported_as_unavailable() {
		RazorpayGateway nowhere = new RazorpayGateway("http://127.0.0.1:1", KEY_ID, KEY_SECRET, WEBHOOK_SECRET);

		assertThatThrownBy(() -> nowhere.createOrder(UUID.randomUUID(), Money.rupees(1600)))
				.isInstanceOf(GatewayUnavailableException.class);
	}

	@Test
	void the_checkout_key_is_the_public_key_id_never_the_secret() {
		assertThat(gateway.checkoutKey()).isEqualTo(KEY_ID);
	}

	// --- the browser's proof of payment ---

	@Test
	void accepts_the_signature_razorpay_describes() throws Exception {
		String signature = hmac(KEY_SECRET, "order_Abc123|pay_Xyz789".getBytes(StandardCharsets.UTF_8));

		assertThat(gateway.isValidCheckoutSignature("order_Abc123", "pay_Xyz789", signature)).isTrue();
	}

	@Test
	void rejects_a_signature_made_with_another_secret() throws Exception {
		String forged = hmac("someone-elses-secret", "order_Abc123|pay_Xyz789".getBytes(StandardCharsets.UTF_8));

		assertThat(gateway.isValidCheckoutSignature("order_Abc123", "pay_Xyz789", forged)).isFalse();
	}

	@Test
	void rejects_a_signature_for_a_different_order_or_payment() throws Exception {
		String signature = hmac(KEY_SECRET, "order_Abc123|pay_Xyz789".getBytes(StandardCharsets.UTF_8));

		assertThat(gateway.isValidCheckoutSignature("order_Other", "pay_Xyz789", signature)).isFalse();
		assertThat(gateway.isValidCheckoutSignature("order_Abc123", "pay_Other", signature)).isFalse();
	}

	@Test
	void rejects_a_missing_or_empty_signature() {
		assertThat(gateway.isValidCheckoutSignature("order_Abc123", "pay_Xyz789", "")).isFalse();
		assertThat(gateway.isValidCheckoutSignature("order_Abc123", "pay_Xyz789", null)).isFalse();
	}

	// --- webhooks ---

	private static final String CAPTURED = """
			{"entity":"event","event":"payment.captured","payload":{"payment":{"entity":{
			"id":"pay_Xyz789","entity":"payment","amount":160000,"currency":"INR","status":"captured",
			"order_id":"order_Abc123","method":"upi"}}}}""";

	@Test
	void reads_a_captured_payment_from_a_correctly_signed_webhook() throws Exception {
		byte[] body = CAPTURED.getBytes(StandardCharsets.UTF_8);

		Optional<GatewayPayment> payment = gateway.readWebhook(body, hmac(WEBHOOK_SECRET, body));

		assertThat(payment).contains(new GatewayPayment("order_Abc123", "pay_Xyz789", Money.rupees(1600), "upi"));
	}

	@Test
	void reads_an_order_paid_webhook_too() throws Exception {
		byte[] body = """
				{"event":"order.paid","payload":{"payment":{"entity":{"id":"pay_Xyz789","amount":160000,
				"order_id":"order_Abc123","method":"netbanking"}},"order":{"entity":{"id":"order_Abc123"}}}}"""
				.getBytes(StandardCharsets.UTF_8);

		Optional<GatewayPayment> payment = gateway.readWebhook(body, hmac(WEBHOOK_SECRET, body));

		assertThat(payment).map(GatewayPayment::gatewayPaymentId).contains("pay_Xyz789");
	}

	@Test
	void rejects_a_webhook_signed_with_the_wrong_secret() throws Exception {
		byte[] body = CAPTURED.getBytes(StandardCharsets.UTF_8);

		assertThatThrownBy(() -> gateway.readWebhook(body, hmac("not-the-secret", body)))
				.isInstanceOf(InvalidWebhookException.class);
	}

	@Test
	void rejects_a_webhook_whose_body_was_changed_after_signing() throws Exception {
		byte[] body = CAPTURED.getBytes(StandardCharsets.UTF_8);
		String signature = hmac(WEBHOOK_SECRET, body);
		byte[] tampered = CAPTURED.replace("160000", "1").getBytes(StandardCharsets.UTF_8);

		assertThatThrownBy(() -> gateway.readWebhook(tampered, signature)).isInstanceOf(InvalidWebhookException.class);
	}

	@Test
	void rejects_a_webhook_with_no_signature() {
		assertThatThrownBy(() -> gateway.readWebhook(CAPTURED.getBytes(StandardCharsets.UTF_8), null))
				.isInstanceOf(InvalidWebhookException.class);
	}

	@Test
	void ignores_events_it_does_not_act_on() throws Exception {
		byte[] body = "{\"event\":\"payment.failed\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_1\"}}}}"
				.getBytes(StandardCharsets.UTF_8);

		assertThat(gateway.readWebhook(body, hmac(WEBHOOK_SECRET, body))).isEmpty();
	}

	@Test
	void rejects_a_correctly_signed_body_that_is_not_json() throws Exception {
		byte[] body = "not json".getBytes(StandardCharsets.UTF_8);

		assertThatThrownBy(() -> gateway.readWebhook(body, hmac(WEBHOOK_SECRET, body)))
				.isInstanceOf(InvalidWebhookException.class);
	}
}
