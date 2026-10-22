package com.societyledger.common.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The web app is served by this same service. Its screens live in the browser, so a link opened
 * directly (a bookmark, a refresh) must load the app and let it show the screen.
 * One-segment paths without a dot are screens; files and the API are left alone.
 */
@Controller
class SpaController {

	@GetMapping("/{screen:^(?!api$|actuator$)[^.]*$}")
	String app() {
		return "forward:/index.html";
	}
}
