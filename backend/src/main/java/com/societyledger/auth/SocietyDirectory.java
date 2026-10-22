package com.societyledger.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Finds a society's id from its public code, which is how login begins. */
@Repository
class SocietyDirectory {

	private final JdbcClient jdbc;

	SocietyDirectory(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	Optional<UUID> idForCode(String code) {
		return jdbc.sql("select society_id_for_code(:code)")
				.param("code", code)
				.query((rs, row) -> rs.getObject(1, UUID.class))
				.optional()
				.flatMap(Optional::ofNullable);
	}
}
