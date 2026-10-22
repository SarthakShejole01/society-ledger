package com.societyledger.export;

import com.societyledger.audit.AuditLog;
import com.societyledger.common.Csv;
import com.societyledger.common.Money;
import com.societyledger.common.tenant.SocietyScope;
import java.sql.Date;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** A month's collection as a spreadsheet file for the treasurer and committee. */
@Service
public class ExportService {

	private static final char BYTE_ORDER_MARK = '﻿';
	private static final List<String> HEADER = List.of("Flat", "Floor", "Status", "Maintenance", "Late fee charged",
			"Late fee waived", "Total paid", "Channel", "Method", "Paid on", "Paid by", "Reference", "Waiver reason");

	private final SocietyScope scope;
	private final JdbcClient jdbc;
	private final AuditLog audit;

	public ExportService(SocietyScope scope, JdbcClient jdbc, AuditLog audit) {
		this.scope = scope;
		this.jdbc = jdbc;
		this.audit = audit;
	}

	public String monthCsv(UUID societyId, UUID memberId, YearMonth month) {
		return scope.inSociety(societyId, () -> {
			StringBuilder csv = new StringBuilder().append(BYTE_ORDER_MARK).append(Csv.row(HEADER));
			jdbc.sql("""
					select f.number, f.floor,
						coalesce(c.amount_paise, f.monthly_amount_paise) as charge_paise,
						p.id is not null as paid,
						p.amount_paise, p.late_fee_paise, p.waived_late_fee_paise,
						p.channel, p.method,
						to_char(p.confirmed_at at time zone 'Asia/Kolkata', 'YYYY-MM-DD HH24:MI') as paid_on,
						p.payer_name, coalesce(p.gateway_reference, p.reference_note) as reference, p.waiver_reason
					from flats f
					left join charges c on c.flat_id = f.id and c.month = :month
					left join payments p on p.charge_id = c.id and p.status = 'CONFIRMED'
					order by f.floor, f.number""")
					.param("month", Date.valueOf(month.atDay(1)))
					.query((rs, row) -> {
						List<String> cells = new ArrayList<>();
						cells.add(rs.getString("number"));
						cells.add(String.valueOf(rs.getInt("floor")));
						if (rs.getBoolean("paid")) {
							long total = rs.getLong("amount_paise");
							long lateFee = rs.getLong("late_fee_paise");
							cells.add("Paid");
							cells.add(Csv.rupees(Money.ofPaise(total - lateFee)));
							cells.add(Csv.rupees(Money.ofPaise(lateFee)));
							cells.add(Csv.rupees(Money.ofPaise(rs.getLong("waived_late_fee_paise"))));
							cells.add(Csv.rupees(Money.ofPaise(total)));
							cells.add(rs.getString("channel"));
							cells.add(rs.getString("method"));
							cells.add(rs.getString("paid_on"));
							cells.add(rs.getString("payer_name"));
							cells.add(rs.getString("reference"));
							cells.add(rs.getString("waiver_reason"));
						}
						else {
							cells.add("Pending");
							cells.add(Csv.rupees(Money.ofPaise(rs.getLong("charge_paise"))));
							for (int i = 0; i < HEADER.size() - 4; i++) {
								cells.add(null);
							}
						}
						return Csv.row(cells);
					})
					.list()
					.forEach(csv::append);
			audit.record(societyId, memberId, "EXPORT_DOWNLOADED", "Collection for " + month);
			return csv.toString();
		});
	}
}
