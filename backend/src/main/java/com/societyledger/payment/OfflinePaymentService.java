package com.societyledger.payment;

import com.societyledger.audit.AuditLog;
import com.societyledger.billing.Charge;
import com.societyledger.billing.ChargeRepository;
import com.societyledger.billing.LateFeePolicy;
import com.societyledger.common.Money;
import com.societyledger.common.tenant.SocietyScope;
import com.societyledger.society.FlatRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/** Records cash and cheque payments received by the treasurer, with an optional late fee waiver. */
@Service
public class OfflinePaymentService {

	private final SocietyScope scope;
	private final ChargeRepository charges;
	private final PaymentRepository payments;
	private final FlatRepository flats;
	private final LateFeePolicy lateFeePolicy;
	private final AuditLog audit;
	private final Clock clock;

	public OfflinePaymentService(SocietyScope scope, ChargeRepository charges, PaymentRepository payments,
			FlatRepository flats, LateFeePolicy lateFeePolicy, AuditLog audit, Clock clock) {
		this.scope = scope;
		this.charges = charges;
		this.payments = payments;
		this.flats = flats;
		this.lateFeePolicy = lateFeePolicy;
		this.audit = audit;
		this.clock = clock;
	}

	public OfflinePaymentResult record(UUID societyId, UUID recordedBy, String recordedByName, OfflinePaymentCommand command) {
		return scope.inSociety(societyId, () -> {
			Charge charge = charges.ensure(command.flatId(), command.month()).orElseThrow(FlatNotFoundException::new);
			if (payments.hasConfirmedPaymentFor(charge.id())) {
				throw new AlreadyPaidException();
			}
			Instant now = Instant.now(clock);
			Money lateFee = lateFeePolicy.feeFor(command.month(), now);
			Money waived = Money.ZERO;
			Money charged = lateFee;
			String waiverReason = null;
			if (command.waiveLateFee() && lateFee.paise() > 0) {
				if (command.waiverReason() == null || command.waiverReason().isBlank()) {
					throw new WaiverReasonRequiredException();
				}
				waived = lateFee;
				charged = Money.ZERO;
				waiverReason = command.waiverReason().trim();
			}
			Money total = charge.amount().plus(charged);
			String flatNumber = flats.findNumber(command.flatId()).orElseThrow(FlatNotFoundException::new);
			String method = command.method().name();
			UUID paymentId;
			try {
				paymentId = payments.insertOffline(societyId, charge.id(),
						capitalised(method) + " recorded by " + recordedByName, total, charged, waived, waiverReason,
						command.method(), command.reference(), recordedBy, now);
			}
			catch (DuplicateKeyException e) {
				throw new AlreadyPaidException();
			}
			audit.record(societyId, recordedBy, "PAYMENT_RECORDED", "Flat %s, %s, %s, %s".formatted(
					flatNumber, command.month(), method, total.formatted()));
			if (waived.paise() > 0) {
				audit.record(societyId, recordedBy, "LATE_FEE_WAIVED", "Flat %s, %s, %s waived. Reason: %s".formatted(
						flatNumber, command.month(), waived.formatted(), waiverReason));
			}
			return new OfflinePaymentResult(paymentId, total, charged, waived);
		});
	}

	private static String capitalised(String text) {
		return text.charAt(0) + text.substring(1).toLowerCase(Locale.ROOT);
	}
}
