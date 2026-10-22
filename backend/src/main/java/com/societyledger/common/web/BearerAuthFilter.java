package com.societyledger.common.web;

import com.societyledger.auth.AuthService;
import com.societyledger.auth.Principal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Requires a valid session token on every /api request except login. */
@Component
class BearerAuthFilter extends OncePerRequestFilter {

	static final String PRINCIPAL_ATTRIBUTE = BearerAuthFilter.class.getName() + ".principal";
	private static final String PREFIX = "Bearer ";

	private final AuthService auth;

	BearerAuthFilter(AuthService auth) {
		this.auth = auth;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		// Login is public, and the payment provider's webhook is trusted by its own signature, not a session.
		return !path.startsWith("/api/") || path.startsWith("/api/auth/") || path.startsWith("/api/gateway/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader("Authorization");
		Optional<Principal> principal = header != null && header.startsWith(PREFIX)
				? auth.authenticate(header.substring(PREFIX.length()).trim())
				: Optional.empty();
		if (principal.isEmpty()) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
			response.setCharacterEncoding(StandardCharsets.UTF_8.name());
			response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,"
					+ "\"detail\":\"Log in to continue\"}");
			return;
		}
		request.setAttribute(PRINCIPAL_ATTRIBUTE, principal.get());
		chain.doFilter(request, response);
	}
}
