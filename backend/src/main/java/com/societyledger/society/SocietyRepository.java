package com.societyledger.society;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SocietyRepository {

	private final JdbcClient jdbc;

	public SocietyRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** The societies visible to the current scope: at most the one the work runs in. */
	public List<Society> findAll() {
		return jdbc.sql("select id, code, name from societies order by code")
				.query((rs, row) -> new Society(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name")))
				.list();
	}
}
