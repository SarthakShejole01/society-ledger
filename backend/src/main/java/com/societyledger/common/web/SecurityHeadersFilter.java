package com.societyledger.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Browser protections on every answer. Content-Security-Policy is deliberately not set here yet: it has to be
 * tested against the Razorpay checkout page first (see docs/security.md).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class SecurityHeadersFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		response.setHeader("X-Content-Type-Options", "nosniff");
		response.setHeader("X-Frame-Options", "DENY");
		response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
		if (request.isSecure()) {
			response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
		}
		if (request.getRequestURI().startsWith("/api/")) {
			response.setHeader("Cache-Control", "no-store");
		}
		chain.doFilter(request, response);
	}
}
