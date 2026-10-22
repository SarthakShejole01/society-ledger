package com.societyledger.audit;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Append-only record of who did what. Call it inside the same society scope as the action it records. */
@Repository
public class AuditLog {

	private final JdbcClient jdbc;

	public AuditLog(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public void record(UUID societyId, UUID memberId, String action, String detail) {
		jdbc.sql("""
				insert into audit_log (id, society_id, member_id, action, detail)
				values (:id, :society, :member, :action, :detail)""")
				.param("id", UUID.randomUUID())
				.param("society", societyId)
				.param("member", memberId)
				.param("action", action)
				.param("detail", detail)
				.update();
	}
}
