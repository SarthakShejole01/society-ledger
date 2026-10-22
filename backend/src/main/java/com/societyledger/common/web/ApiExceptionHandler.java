package com.societyledger.common.web;

import com.societyledger.auth.InvalidOtpException;
import com.societyledger.auth.TooManyOtpRequestsException;
import com.societyledger.payment.AlreadyPaidException;
import com.societyledger.payment.AmountMismatchException;
import com.societyledger.payment.FlatNotFoundException;
import com.societyledger.payment.GatewayReferenceReusedException;
import com.societyledger.payment.GatewayUnavailableException;
import com.societyledger.payment.InvalidSignatureException;
import com.societyledger.payment.InvalidWebhookException;
import com.societyledger.payment.WaiverReasonRequiredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Every error leaves the API as an RFC 7807 problem. */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(InvalidOtpException.class)
	ProblemDetail invalidOtp(InvalidOtpException e) {
		return problem(HttpStatus.UNAUTHORIZED, e.getMessage());
	}

	@ExceptionHandler(TooManyOtpRequestsException.class)
	ProblemDetail tooManyOtpRequests(TooManyOtpRequestsException e) {
		return problem(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
	}

	@ExceptionHandler(WaiverReasonRequiredException.class)
	ProblemDetail waiverReasonRequired(WaiverReasonRequiredException e) {
		return problem(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	@ExceptionHandler({ InvalidSignatureException.class, InvalidWebhookException.class })
	ProblemDetail untrusted(RuntimeException e) {
		return problem(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	@ExceptionHandler(GatewayUnavailableException.class)
	ProblemDetail gatewayUnavailable(GatewayUnavailableException e) {
		return problem(HttpStatus.BAD_GATEWAY, "The payment provider is not available. Try again in a moment.");
	}

	@ExceptionHandler(ForbiddenException.class)
	ProblemDetail forbidden(ForbiddenException e) {
		return problem(HttpStatus.FORBIDDEN, e.getMessage());
	}

	@ExceptionHandler(FlatNotFoundException.class)
	ProblemDetail flatNotFound(FlatNotFoundException e) {
		return problem(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler({ AlreadyPaidException.class, AmountMismatchException.class, GatewayReferenceReusedException.class })
	ProblemDetail conflict(RuntimeException e) {
		return problem(HttpStatus.CONFLICT, e.getMessage());
	}

	private ProblemDetail problem(HttpStatus status, String detail) {
		return ProblemDetail.forStatusAndDetail(status, detail);
	}
}
