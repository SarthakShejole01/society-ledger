package com.societyledger.society;

import com.societyledger.common.Money;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class FlatRepository {

	private final JdbcClient jdbc;

	public FlatRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Flat add(UUID societyId, String number, int floor, Money monthlyAmount) {
		UUID id = UUID.randomUUID();
		jdbc.sql("""
				insert into flats (id, society_id, number, floor, monthly_amount_paise)
				values (:id, :societyId, :number, :floor, :amount)""")
				.param("id", id)
				.param("societyId", societyId)
				.param("number", number)
				.param("floor", floor)
				.param("amount", monthlyAmount.paise())
				.update();
		return new Flat(id, societyId, number, floor, monthlyAmount);
	}

	/** The flats visible to the current scope. */
	public List<Flat> findAll() {
		return jdbc.sql("""
				select id, society_id, number, floor, monthly_amount_paise
				from flats order by floor, number""")
				.query((rs, row) -> new Flat(
						rs.getObject("id", UUID.class),
						rs.getObject("society_id", UUID.class),
						rs.getString("number"),
						rs.getInt("floor"),
						Money.ofPaise(rs.getLong("monthly_amount_paise"))))
				.list();
	}

	/** The number of a flat visible to the current scope. */
	public Optional<String> findNumber(UUID flatId) {
		return jdbc.sql("select number from flats where id = :id")
				.param("id", flatId)
				.query(String.class)
				.optional();
	}
}
