package com.societyledger.common.tenant;

import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs work inside one society. The database only returns that society's rows for the duration
 * of the transaction; outside a scope it returns none.
 */
@Component
public class SocietyScope {

	private final TransactionTemplate transaction;
	private final JdbcClient jdbc;

	public SocietyScope(PlatformTransactionManager transactionManager, JdbcClient jdbc) {
		this.transaction = new TransactionTemplate(transactionManager);
		this.jdbc = jdbc;
	}

	public <T> T inSociety(UUID societyId, Supplier<T> work) {
		return transaction.execute(status -> {
			jdbc.sql("select set_config('app.society_id', :id, true)")
					.param("id", societyId.toString())
					.query(String.class)
					.single();
			return work.get();
		});
	}
}
