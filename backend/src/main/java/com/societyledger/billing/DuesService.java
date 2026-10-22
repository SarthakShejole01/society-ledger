package com.societyledger.billing;

import com.societyledger.common.Money;
import com.societyledger.common.tenant.SocietyScope;
import com.societyledger.payment.FlatNotFoundException;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Works out what a flat owes right now, without creating or changing anything. */
@Service
public class DuesService {

	private record Row(String number, Money amount, boolean paid) {
	}

	private final SocietyScope scope;
	private final JdbcClient jdbc;
	private final LateFeePolicy lateFeePolicy;
	private final Clock clock;

	public DuesService(SocietyScope scope, JdbcClient jdbc, LateFeePolicy lateFeePolicy, Clock clock) {
		this.scope = scope;
		this.jdbc = jdbc;
		this.lateFeePolicy = lateFeePolicy;
		this.clock = clock;
	}

	public Dues forFlat(UUID societyId, UUID flatId, YearMonth month) {
		Row row = scope.inSociety(societyId, () -> jdbc.sql("""
				select f.number,
					coalesce(c.amount_paise, f.monthly_amount_paise) as amount_paise,
					exists (select 1 from payments p where p.charge_id = c.id and p.status = 'CONFIRMED') as paid
				from flats f
				left join charges c on c.flat_id = f.id and c.month = :month
				where f.id = :flatId""")
				.param("month", Date.valueOf(month.atDay(1)))
				.param("flatId", flatId)
				.query((rs, n) -> new Row(rs.getString("number"), Money.ofPaise(rs.getLong("amount_paise")), rs.getBoolean("paid")))
				.optional()
				.orElseThrow(FlatNotFoundException::new));
		if (row.paid()) {
			return new Dues(row.number(), month, row.amount(), Money.ZERO, Money.ZERO, true);
		}
		Money lateFee = lateFeePolicy.feeFor(month, Instant.now(clock));
		return new Dues(row.number(), month, row.amount(), lateFee, row.amount().plus(lateFee), false);
	}
}
