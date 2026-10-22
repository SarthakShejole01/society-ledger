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

	record IntentResponse(UUID paymentId, UUID flatId, String month, long totalPaise, long lateFeePaise) {
	}

	private final PaymentService payments;

	PaymentController(PaymentService payments) {
		this.payments = payments;
	}

	@PostMapping("/api/payments")
	@ResponseStatus(HttpStatus.CREATED)
	IntentResponse start(@AuthenticatedPrincipal Principal who, @Valid @RequestBody StartRequest request) {
		if (who.role() == Role.COMMITTEE) {
			throw new ForbiddenException();
		}
		PaymentIntent intent = payments.start(who.societyId(), request.flatId(), request.month(), who.name());
		return new IntentResponse(intent.paymentId(), intent.flatId(), intent.month().toString(),
				intent.total().paise(), intent.lateFee().paise());
	}
}
