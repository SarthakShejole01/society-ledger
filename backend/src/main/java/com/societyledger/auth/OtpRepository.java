package com.societyledger.auth;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class OtpRepository {

	record Challenge(UUID id, String codeHash, int attempts) {
	}

	private final JdbcClient jdbc;

	OtpRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	int countRequestsSince(UUID memberId, Instant since) {
		return jdbc.sql("select count(*) from otp_challenges where member_id = :member and created_at > :since")
				.param("member", memberId)
				.param("since", Timestamp.from(since))
				.query(Integer.class)
				.single();
	}

	void invalidateActive(UUID memberId) {
		jdbc.sql("update otp_challenges set used = true where member_id = :member and not used")
				.param("member", memberId)
				.update();
	}

	void insert(UUID societyId, UUID memberId, String codeHash, Instant createdAt, Instant expiresAt) {
		jdbc.sql("""
				insert into otp_challenges (id, society_id, member_id, code_hash, created_at, expires_at)
				values (:id, :society, :member, :hash, :created, :expires)""")
				.param("id", UUID.randomUUID())
				.param("society", societyId)
				.param("member", memberId)
				.param("hash", codeHash)
				.param("created", Timestamp.from(createdAt))
				.param("expires", Timestamp.from(expiresAt))
				.update();
	}

	Optional<Challenge> latestActive(UUID memberId, Instant now) {
		return jdbc.sql("""
				select id, code_hash, attempts from otp_challenges
				where member_id = :member and not used and expires_at > :now
				order by created_at desc limit 1""")
				.param("member", memberId)
				.param("now", Timestamp.from(now))
				.query((rs, row) -> new Challenge(rs.getObject("id", UUID.class), rs.getString("code_hash"), rs.getInt("attempts")))
				.optional();
	}

	void recordFailedAttempt(UUID challengeId) {
		jdbc.sql("update otp_challenges set attempts = attempts + 1 where id = :id").param("id", challengeId).update();
	}

	void markUsed(UUID challengeId) {
		jdbc.sql("update otp_challenges set used = true where id = :id").param("id", challengeId).update();
	}
}
