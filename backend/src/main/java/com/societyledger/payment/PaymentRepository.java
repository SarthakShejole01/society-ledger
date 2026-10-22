package com.societyledger.payment;

import com.societyledger.common.Money;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class PaymentRepository {

	record Row(UUID id, UUID chargeId, UUID flatId, String status, Money amount, String gatewayReference) {
	}

	private final JdbcClient jdbc;

	PaymentRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	boolean hasConfirmedPaymentFor(UUID chargeId) {
		return jdbc.sql("select exists (select 1 from payments where charge_id = :chargeId and status = 'CONFIRMED')")
				.param("chargeId", chargeId)
				.query(Boolean.class)
				.single();
	}

	UUID insertPending(UUID societyId, UUID chargeId, String payerName, Money total, Money lateFee, Instant startedAt) {
		UUID id = UUID.randomUUID();
		jdbc.sql("""
				insert into payments (id, society_id, charge_id, payer_name, status, amount_paise, late_fee_paise, started_at)
				values (:id, :societyId, :chargeId, :payer, 'PENDING', :total, :lateFee, :startedAt)""")
				.param("id", id)
				.param("societyId", societyId)
				.param("chargeId", chargeId)
				.param("payer", payerName)
				.param("total", total.paise())
				.param("lateFee", lateFee.paise())
				.param("startedAt", java.sql.Timestamp.from(startedAt))
				.update();
		return id;
	}

	Optional<Row> findById(UUID paymentId) {
		return jdbc.sql(rowSql() + " where p.id = :id").param("id", paymentId).query(this::row).optional();
	}

	Optional<Row> findByReference(String gatewayReference) {
		return jdbc.sql(rowSql() + " where p.gateway_reference = :ref").param("ref", gatewayReference).query(this::row).optional();
	}

	void markConfirmed(UUID paymentId, String gatewayReference, String method, Instant confirmedAt) {
		jdbc.sql("""
				update payments set status = 'CONFIRMED', gateway_reference = :ref, method = :method, confirmed_at = :at
				where id = :id and status = 'PENDING'""")
				.param("id", paymentId)
				.param("ref", gatewayReference)
				.param("method", method)
				.param("at", java.sql.Timestamp.from(confirmedAt))
				.update();
	}

	private String rowSql() {
		return """
				select p.id, p.charge_id, c.flat_id, p.status, p.amount_paise, p.gateway_reference
				from payments p join charges c on c.id = p.charge_id""";
	}

	private Row row(java.sql.ResultSet rs, int rowNumber) throws java.sql.SQLException {
		return new Row(rs.getObject("id", UUID.class), rs.getObject("charge_id", UUID.class),
				rs.getObject("flat_id", UUID.class), rs.getString("status"),
				Money.ofPaise(rs.getLong("amount_paise")), rs.getString("gateway_reference"));
	}
}
