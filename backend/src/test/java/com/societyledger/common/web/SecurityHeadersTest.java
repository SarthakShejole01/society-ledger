package com.societyledger.common.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.societyledger.auth.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SpaController.class)
class SecurityHeadersTest {

	@Autowired
	private MockMvc mvc;
	@MockitoBean
	private AuthService auth;

	@Test
	void stops_browsers_guessing_a_file_type() throws Exception {
		mvc.perform(get("/login")).andExpect(header().string("X-Content-Type-Options", "nosniff"));
	}

	@Test
	void stops_other_sites_putting_the_app_in_a_frame() throws Exception {
		mvc.perform(get("/login")).andExpect(header().string("X-Frame-Options", "DENY"));
	}

	@Test
	void does_not_leak_the_address_to_other_sites() throws Exception {
		mvc.perform(get("/login")).andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
	}

	@Test
	void asks_browsers_to_stay_on_https_when_the_request_came_in_over_https() throws Exception {
		mvc.perform(get("/login").secure(true))
				.andExpect(header().string("Strict-Transport-Security", "max-age=31536000; includeSubDomains"));
	}

	@Test
	void does_not_send_the_https_rule_over_plain_http() throws Exception {
		mvc.perform(get("/login")).andExpect(header().doesNotExist("Strict-Transport-Security"));
	}

	@Test
	void api_answers_are_never_cached_even_a_refusal() throws Exception {
		mvc.perform(get("/api/dashboard"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"));
	}

	@Test
	void screens_are_not_marked_no_store_so_the_app_files_can_be_cached() throws Exception {
		mvc.perform(get("/login")).andExpect(header().doesNotExist("Cache-Control"));
	}
}
