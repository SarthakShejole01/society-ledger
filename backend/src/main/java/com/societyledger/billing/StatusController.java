package com.societyledger.billing;

import com.societyledger.auth.Principal;
import com.societyledger.common.web.AuthenticatedPrincipal;
import java.time.YearMonth;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class StatusController {

	private final StatusBoardService board;

	StatusController(StatusBoardService board) {
		this.board = board;
	}

	/** Paid or Pending for every flat, with no amounts, visible to every member. */
	@GetMapping("/api/flats/status")
	List<FlatStatus> status(@AuthenticatedPrincipal Principal who,
			@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
		return board.forMonth(who.societyId(), month);
	}
}
