package com.societyledger.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

	record OtpRequest(@NotBlank String societyCode, @NotBlank String mobile) {
	}

	record VerifyRequest(@NotBlank String societyCode, @NotBlank String mobile,
			@NotBlank @Pattern(regexp = "\\d{6}") String code) {
	}

	record SessionResponse(String token, Role role, UUID flatId, String name) {
	}

	private final AuthService auth;

	AuthController(AuthService auth) {
		this.auth = auth;
	}

	@PostMapping("/otp")
	ResponseEntity<Void> requestOtp(@Valid @RequestBody OtpRequest request) {
		auth.requestOtp(request.societyCode().trim(), request.mobile().trim());
		return ResponseEntity.accepted().build();
	}

	@PostMapping("/verify")
	SessionResponse verify(@Valid @RequestBody VerifyRequest request) {
		AuthSession session = auth.verifyOtp(request.societyCode().trim(), request.mobile().trim(), request.code());
		Principal who = session.principal();
		return new SessionResponse(session.token(), who.role(), who.flatId(), who.name());
	}
}
