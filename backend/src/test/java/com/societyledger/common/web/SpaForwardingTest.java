package com.societyledger.common.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.societyledger.auth.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The web app lives in the same service, so a link opened directly must still load it. */
@WebMvcTest(controllers = SpaController.class)
class SpaForwardingTest {

	@Autowired
	private MockMvc mvc;
	@MockitoBean
	private AuthService auth;

	@Test
	void opening_a_screen_directly_loads_the_app() throws Exception {
		mvc.perform(get("/pay-for")).andExpect(forwardedUrl("/index.html"));
	}

	@Test
	void every_screen_loads_the_app() throws Exception {
		for (String screen : new String[] { "/login", "/home", "/flats", "/dashboard", "/record" }) {
			mvc.perform(get(screen)).andExpect(forwardedUrl("/index.html"));
		}
	}

	@Test
	void a_screen_added_later_needs_no_change_here() throws Exception {
		mvc.perform(get("/some-new-screen")).andExpect(forwardedUrl("/index.html"));
	}

	@Test
	void files_are_not_turned_into_the_app() throws Exception {
		mvc.perform(get("/main-ABC123.js")).andExpect(status().isNotFound());
	}

	@Test
	void the_api_is_never_turned_into_the_app() throws Exception {
		mvc.perform(get("/api/flats/status").param("month", "2026-10")).andExpect(status().isUnauthorized());
	}

	@Test
	void unknown_api_paths_are_not_turned_into_the_app() throws Exception {
		mvc.perform(get("/api")).andExpect(status().isNotFound());
	}
}
