package com.societyledger.billing;

import com.societyledger.auth.Principal;
import com.societyledger.auth.Role;
import com.societyledger.common.web.AuthenticatedPrincipal;
import com.societyledger.common.web.ForbiddenException;
import java.time.YearMonth;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class DashboardController {

	record PendingResponse(String number, int floor, long dueNowPaise) {
	}

	record DashboardResponse(String month, int flatCount, int paidCount, int pendingCount, long expectedPaise,
			long collectedPaise, long onlineCollectedPaise, long offlineCollectedPaise, long lateFeesCollectedPaise,
			long lateFeesWaivedPaise, List<PendingResponse> pending) {
	}

	private final DashboardService dashboard;

	DashboardController(DashboardService dashboard) {
		this.dashboard = dashboard;
	}

	@GetMapping("/api/dashboard")
	DashboardResponse dashboard(@AuthenticatedPrincipal Principal who,
			@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
		if (who.role() == Role.OWNER) {
			throw new ForbiddenException();
		}
		Dashboard d = dashboard.forMonth(who.societyId(), month);
		return new DashboardResponse(d.month().toString(), d.flatCount(), d.paidCount(), d.pendingCount(),
				d.expected().paise(), d.collected().paise(), d.onlineCollected().paise(), d.offlineCollected().paise(),
				d.lateFeesCollected().paise(), d.lateFeesWaived().paise(),
				d.pending().stream().map(p -> new PendingResponse(p.number(), p.floor(), p.dueNow().paise())).toList());
	}
}
