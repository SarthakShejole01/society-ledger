package com.societyledger.payment;

import com.societyledger.common.Money;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Razorpay: orders are created on the server in paise, the customer pays on Razorpay's page, and a
 * payment counts only when its HMAC signature checks out, either from the browser or from a webhook.
 */
@Component
@ConditionalOnProperty(prefix = "society-ledger.gateway", name = "provider", havingValue = "razorpay")
class RazorpayGateway implements PaymentGateway {

	private static final Set<String> PAID_EVENTS = Set.of("payment.captured", "order.paid");

	private final RestClient http;
	private final String keyId;
	private final byte[] keySecret;
	private final byte[] webhookSecret;

	RazorpayGateway(@Value("${society-ledger.gateway.razorpay.base-url:https://api.razorpay.com}") String baseUrl,
			@Value("${society-ledger.gateway.razorpay.key-id}") String keyId,
			@Value("${society-ledger.gateway.razorpay.key-secret}") String keySecret,
			@Value("${society-ledger.gateway.razorpay.webhook-secret}") String webhookSecret) {
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
		factory.setReadTimeout(Duration.ofSeconds(10));
		String basic = Base64.getEncoder().encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
		this.http = RestClient.builder()
				.baseUrl(baseUrl)
				.requestFactory(factory)
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
				.build();
		this.keyId = keyId;
		this.keySecret = keySecret.getBytes(StandardCharsets.UTF_8);
		this.webhookSecret = webhookSecret.getBytes(StandardCharsets.UTF_8);
	}

	@Override
	public GatewayOrder createOrder(UUID paymentId, Money amount) {
		try {
			Map<?, ?> reply = http.post().uri("/v1/orders")
					.body(Map.of("amount", amount.paise(), "currency", "INR", "receipt", paymentId.toString(),
							"notes", Map.of("payment_id", paymentId.toString())))
					.retrieve()
					.body(Map.class);
			Object id = reply == null ? null : reply.get("id");
			if (!(id instanceof String orderId) || orderId.isBlank()) {
				throw new IllegalStateException("The provider's reply had no order id");
			}
			return new GatewayOrder(orderId);
		}
		catch (RuntimeException e) {
			throw new GatewayUnavailableException(e);
		}
	}

	@Override
	public boolean isValidCheckoutSignature(String orderId, String gatewayPaymentId, String signature) {
		if (signature == null || signature.isBlank()) {
			return false;
		}
		return constantTimeEquals(hmacHex(keySecret, (orderId + "|" + gatewayPaymentId).getBytes(StandardCharsets.UTF_8)), signature);
	}

	@Override
	public Optional<GatewayPayment> readWebhook(byte[] body, String signature) {
		if (signature == null || !constantTimeEquals(hmacHex(webhookSecret, body), signature)) {
			throw new InvalidWebhookException();
		}
		Map<String, Object> event;
		try {
			event = JsonParserFactory.getJsonParser().parseMap(new String(body, StandardCharsets.UTF_8));
		}
		catch (RuntimeException e) {
			throw new InvalidWebhookException();
		}
		if (!(event.get("event") instanceof String type) || !PAID_EVENTS.contains(type)) {
			return Optional.empty();
		}
		Map<?, ?> payment = entity(event, "payment");
		if (payment == null || !(payment.get("id") instanceof String id) || !(payment.get("order_id") instanceof String orderId)
				|| !(payment.get("amount") instanceof Number amount)) {
			throw new InvalidWebhookException();
		}
		String method = payment.get("method") instanceof String m ? m : "online";
		return Optional.of(new GatewayPayment(orderId, id, Money.ofPaise(amount.longValue()), method));
	}

	@Override
	public String checkoutKey() {
		return keyId;
	}

	private static Map<?, ?> entity(Map<String, Object> event, String name) {
		if (event.get("payload") instanceof Map<?, ?> payload && payload.get(name) instanceof Map<?, ?> wrapper
				&& wrapper.get("entity") instanceof Map<?, ?> entity) {
			return entity;
		}
		return null;
	}

	private static String hmacHex(byte[] key, byte[] data) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(key, "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(data));
		}
		catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException(e);
		}
	}

	private static boolean constantTimeEquals(String expected, String actual) {
		return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
	}
}
