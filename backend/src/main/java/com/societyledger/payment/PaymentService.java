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
	private final Clock clock;

	public PaymentService(SocietyScope scope, ChargeRepository charges, PaymentRepository payments,
			LateFeePolicy lateFeePolicy, Clock clock) {
		this.scope = scope;
		this.charges = charges;
		this.payments = payments;
		this.lateFeePolicy = lateFeePolicy;
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
			return new PaymentIntent(paymentId, flatId, month, total, lateFee);
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
}
