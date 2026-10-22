package com.societyledger.export;

import com.societyledger.auth.Principal;
import com.societyledger.auth.Role;
import com.societyledger.common.web.AuthenticatedPrincipal;
import com.societyledger.common.web.ForbiddenException;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ExportController {

	private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

	private final ExportService export;

	ExportController(ExportService export) {
		this.export = export;
	}

	@GetMapping("/api/exports/month.csv")
	ResponseEntity<byte[]> month(@AuthenticatedPrincipal Principal who,
			@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
		if (who.role() == Role.OWNER) {
			throw new ForbiddenException();
		}
		byte[] body = export.monthCsv(who.societyId(), who.memberId(), month).getBytes(StandardCharsets.UTF_8);
		return ResponseEntity.ok()
				.contentType(CSV)
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename("maintenance-" + month + ".csv").build().toString())
				.body(body);
	}
}
