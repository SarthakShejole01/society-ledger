package com.societyledger.auth;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class SessionRepository {

	private final JdbcClient jdbc;

	SessionRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	void insert(String tokenHash, UUID societyId, UUID memberId, Instant createdAt, Instant expiresAt) {
		jdbc.sql("""
				insert into sessions (token_hash, society_id, member_id, created_at, expires_at)
				values (:hash, :society, :member, :created, :expires)""")
				.param("hash", tokenHash)
				.param("society", societyId)
				.param("member", memberId)
				.param("created", Timestamp.from(createdAt))
				.param("expires", Timestamp.from(expiresAt))
				.update();
	}

	Optional<UUID> memberFor(String tokenHash, Instant now) {
		return jdbc.sql("select member_id from sessions where token_hash = :hash and expires_at > :now")
				.param("hash", tokenHash)
				.param("now", Timestamp.from(now))
				.query((rs, row) -> rs.getObject("member_id", UUID.class))
				.optional();
	}
}
