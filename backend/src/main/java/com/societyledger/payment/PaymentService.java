package com.societyledger.payment;

import com.societyledger.billing.Charge;
import com.societyledger.billing.ChargeRepository;
import com.societyledger.billing.LateFeePolicy;
import com.societyledger.common.Money;
import com.societyledger.common.tenant.SocietyScope;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Starts and confirms maintenance payments. The server decides the amount when a payment is
 * started, and a payment counts only when the gateway confirms exactly that amount.
 */
@Service
public class PaymentService {

	private final SocietyScope scope;
	private final ChargeRepository charges;
	private final PaymentRepository payments;
	private final LateFeePolicy lateFeePolicy;
	private final PaymentGateway gateway;
	private final Clock clock;

	public PaymentService(SocietyScope scope, ChargeRepository charges, PaymentRepository payments,
			LateFeePolicy lateFeePolicy, PaymentGateway gateway, Clock clock) {
		this.scope = scope;
		this.charges = charges;
		this.payments = payments;
		this.lateFeePolicy = lateFeePolicy;
		this.gateway = gateway;
		this.clock = clock;
	}

	public PaymentIntent start(UUID societyId, UUID flatId, YearMonth month, String payerName) {
		return scope.inSociety(societyId, () -> {
			Charge charge = charges.ensure(flatId, month).orElseThrow(FlatNotFoundException::new);
			if (payments.hasConfirmedPaymentFor(charge.id())) {
				throw new AlreadyPaidException();
			}
			Instant startedAt = Instant.now(clock);
			Money lateFee = lateFeePolicy.feeFor(month, startedAt);
			Money total = charge.amount().plus(lateFee);
			UUID paymentId = payments.insertPending(societyId, charge.id(), payerName, total, lateFee, startedAt);
			// If the provider cannot be reached this throws, and the pending payment is rolled back with it.
			GatewayOrder order = gateway.createOrder(paymentId, total);
			payments.setOrderId(paymentId, order.orderId());
			return new PaymentIntent(paymentId, flatId, month, total, lateFee, order.orderId(), gateway.checkoutKey());
		});
	}

	/** Safe to call more than once with the same gateway reference. */
	public void confirm(UUID societyId, UUID paymentId, String gatewayReference, String method, Money amountPaid) {
		scope.inSociety(societyId, () -> {
			PaymentRepository.Row payment = payments.findById(paymentId).orElseThrow(FlatNotFoundException::new);
			if (payment.status().equals("CONFIRMED")) {
				if (gatewayReference.equals(payment.gatewayReference())) {
					return null;
				}
				throw new GatewayReferenceReusedException();
			}
			if (!amountPaid.equals(payment.amount())) {
				throw new AmountMismatchException();
			}
			if (payments.findByReference(gatewayReference).isPresent()) {
				throw new GatewayReferenceReusedException();
			}
			try {
				payments.markConfirmed(paymentId, gatewayReference, method, Instant.now(clock));
			}
			catch (DuplicateKeyException e) {
				throw new GatewayReferenceReusedException();
			}
			return null;
		});
	}

	/**
	 * The browser reports a finished payment. It counts only when the proof is signed by the provider for the
	 * order we created, which is looked up from our own record and never taken from the browser.
	 */
	public void confirmCheckout(UUID societyId, UUID paymentId, String orderId, String gatewayPaymentId, String signature) {
		scope.inSociety(societyId, () -> {
			PaymentRepository.Row payment = payments.findById(paymentId).orElseThrow(FlatNotFoundException::new);
			boolean matches = payment.gatewayOrderId() != null && payment.gatewayOrderId().equals(orderId)
					&& gateway.isValidCheckoutSignature(payment.gatewayOrderId(), gatewayPaymentId, signature);
			if (!matches) {
				throw new InvalidSignatureException();
			}
			confirm(societyId, paymentId, gatewayPaymentId, "ONLINE", payment.amount());
			return null;
		});
	}

	/** The provider reports a payment. Orders we do not know are ignored, and repeats are harmless. */
	public void confirmFromWebhook(GatewayPayment event) {
		payments.contextForOrder(event.orderId()).ifPresent(context ->
				confirm(context.societyId(), context.paymentId(), event.gatewayPaymentId(), event.method(), event.amount()));
	}
}
