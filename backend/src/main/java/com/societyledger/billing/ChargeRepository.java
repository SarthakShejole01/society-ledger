package com.societyledger.billing;

import com.societyledger.common.Money;
import java.sql.Date;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ChargeRepository {

	private final JdbcClient jdbc;

	public ChargeRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * The charge for a flat and month, created from the flat's monthly amount if it does not exist yet.
	 * Empty when the flat is not visible to the current society.
	 */
	public Optional<Charge> ensure(UUID flatId, YearMonth month) {
		jdbc.sql("""
				insert into charges (id, society_id, flat_id, month, amount_paise)
				select gen_random_uuid(), society_id, id, :month, monthly_amount_paise
				from flats where id = :flatId
				on conflict (flat_id, month) do nothing""")
				.param("month", Date.valueOf(month.atDay(1)))
				.param("flatId", flatId)
				.update();
		return jdbc.sql("select id, flat_id, month, amount_paise from charges where flat_id = :flatId and month = :month")
				.param("flatId", flatId)
				.param("month", Date.valueOf(month.atDay(1)))
				.query((rs, row) -> new Charge(
						rs.getObject("id", UUID.class),
						rs.getObject("flat_id", UUID.class),
						YearMonth.from(rs.getDate("month").toLocalDate()),
						Money.ofPaise(rs.getLong("amount_paise"))))
				.optional();
	}
}
