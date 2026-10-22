package com.societyledger.billing;

import com.societyledger.auth.Principal;
import com.societyledger.auth.Role;
import com.societyledger.common.web.AuthenticatedPrincipal;
import com.societyledger.common.web.ForbiddenException;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class DuesController {

	record DuesResponse(String flatNumber, String month, long amountPaise, long lateFeePaise, long totalPaise, boolean paid) {
	}

	private final DuesService dues;

	DuesController(DuesService dues) {
		this.dues = dues;
	}

	/** An owner sees only their own flat's amounts. The treasurer and committee see any flat in the society. */
	@GetMapping("/api/flats/{flatId}/dues")
	DuesResponse dues(@AuthenticatedPrincipal Principal who, @PathVariable UUID flatId,
			@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
		boolean ownFlat = who.role() == Role.OWNER && flatId.equals(who.flatId());
		if (!ownFlat && who.role() == Role.OWNER) {
			throw new ForbiddenException();
		}
		Dues result = dues.forFlat(who.societyId(), flatId, month);
		return new DuesResponse(result.flatNumber(), result.month().toString(), result.amount().paise(),
				result.lateFee().paise(), result.total().paise(), result.paid());
	}
}
