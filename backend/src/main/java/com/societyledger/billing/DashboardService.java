package com.societyledger.billing;

import com.societyledger.common.Money;
import com.societyledger.common.tenant.SocietyScope;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Month summary for a society, all worked out in SQL. */
@Service
public class DashboardService {

	private final SocietyScope scope;
	private final JdbcClient jdbc;
	private final LateFeePolicy lateFeePolicy;
	private final Clock clock;

	public DashboardService(SocietyScope scope, JdbcClient jdbc, LateFeePolicy lateFeePolicy, Clock clock) {
		this.scope = scope;
		this.jdbc = jdbc;
		this.lateFeePolicy = lateFeePolicy;
		this.clock = clock;
	}

	public Dashboard forMonth(UUID societyId, YearMonth month) {
		Date firstDay = Date.valueOf(month.atDay(1));
		Money lateFeeNow = lateFeePolicy.feeFor(month, Instant.now(clock));
		return scope.inSociety(societyId, () -> {
			long[] flatTotals = jdbc.sql("""
					select count(*) as flat_count,
						coalesce(sum(coalesce(c.amount_paise, f.monthly_amount_paise)), 0) as expected
					from flats f left join charges c on c.flat_id = f.id and c.month = :month""")
					.param("month", firstDay)
					.query((rs, row) -> new long[] { rs.getLong("flat_count"), rs.getLong("expected") })
					.single();
			long[] paid = jdbc.sql("""
					select count(*) as paid_count,
						coalesce(sum(p.amount_paise), 0) as collected,
						coalesce(sum(case when p.channel = 'ONLINE' then p.amount_paise else 0 end), 0) as online,
						coalesce(sum(p.late_fee_paise), 0) as fees,
						coalesce(sum(p.waived_late_fee_paise), 0) as waived
					from payments p join charges c on c.id = p.charge_id
					where c.month = :month and p.status = 'CONFIRMED'""")
					.param("month", firstDay)
					.query((rs, row) -> new long[] { rs.getLong("paid_count"), rs.getLong("collected"), rs.getLong("online"),
							rs.getLong("fees"), rs.getLong("waived") })
					.single();
			List<PendingFlat> pending = jdbc.sql("""
					select f.id, f.number, f.floor, coalesce(c.amount_paise, f.monthly_amount_paise) as amount_paise
					from flats f left join charges c on c.flat_id = f.id and c.month = :month
					where not exists (select 1 from payments p where p.charge_id = c.id and p.status = 'CONFIRMED')
					order by f.floor, f.number""")
					.param("month", firstDay)
					.query((rs, row) -> {
						Money amount = Money.ofPaise(rs.getLong("amount_paise"));
						return new PendingFlat(rs.getObject("id", UUID.class), rs.getString("number"), rs.getInt("floor"),
								amount, amount.plus(lateFeeNow));
					})
					.list();
			return new Dashboard(month, (int) flatTotals[0], (int) paid[0], pending.size(), Money.ofPaise(flatTotals[1]),
					Money.ofPaise(paid[1]), Money.ofPaise(paid[2]), Money.ofPaise(paid[1] - paid[2]),
					Money.ofPaise(paid[3]), Money.ofPaise(paid[4]), pending);
		});
	}
}
