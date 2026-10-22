package com.societyledger.payment;

import com.societyledger.auth.Principal;
import com.societyledger.auth.Role;
import com.societyledger.common.web.AuthenticatedPrincipal;
import com.societyledger.common.web.ForbiddenException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PaymentController {

	/** There is deliberately no amount here: the server decides what is owed. */
	record StartRequest(@NotNull UUID flatId, @NotNull YearMonth month) {
	}

	record OfflineRequest(@NotNull UUID flatId, @NotNull YearMonth month, @NotNull OfflineMethod method,
			String reference, boolean waiveLateFee, String waiverReason) {
	}

	record OfflineResponse(UUID paymentId, long totalPaise, long lateFeePaise, long waivedPaise) {
	}

	record IntentResponse(UUID paymentId, UUID flatId, String month, long totalPaise, long lateFeePaise) {
	}

	private final PaymentService payments;
	private final OfflinePaymentService offline;

	PaymentController(PaymentService payments, OfflinePaymentService offline) {
		this.payments = payments;
		this.offline = offline;
	}

	/** Cash or cheque received by the treasurer. Only the treasurer can record one. */
	@PostMapping("/api/payments/offline")
	@ResponseStatus(HttpStatus.CREATED)
	OfflineResponse recordOffline(@AuthenticatedPrincipal Principal who, @Valid @RequestBody OfflineRequest request) {
		if (who.role() != Role.TREASURER) {
			throw new ForbiddenException();
		}
		OfflinePaymentResult result = offline.record(who.societyId(), who.memberId(), who.name(),
				new OfflinePaymentCommand(request.flatId(), request.month(), request.method(), request.reference(),
						request.waiveLateFee(), request.waiverReason()));
		return new OfflineResponse(result.paymentId(), result.total().paise(), result.lateFee().paise(), result.waived().paise());
	}

	@PostMapping("/api/payments")
	@ResponseStatus(HttpStatus.CREATED)
	IntentResponse start(@AuthenticatedPrincipal Principal who, @Valid @RequestBody StartRequest request) {
		// The committee is otherwise read-only, but a member who owns a flat may pay for their own.
		if (who.role() == Role.COMMITTEE && !request.flatId().equals(who.flatId())) {
			throw new ForbiddenException();
		}
		PaymentIntent intent = payments.start(who.societyId(), request.flatId(), request.month(), who.name());
		return new IntentResponse(intent.paymentId(), intent.flatId(), intent.month().toString(),
				intent.total().paise(), intent.lateFee().paise());
	}
}
