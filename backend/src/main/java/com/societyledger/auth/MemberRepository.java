package com.societyledger.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class MemberRepository {

	record Member(UUID id, UUID societyId, UUID flatId, Role role, String email, String name) {
	}

	private final JdbcClient jdbc;

	MemberRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	Optional<Member> findByMobile(String mobile) {
		return jdbc.sql("select id, society_id, flat_id, role, email, name from members where mobile = :mobile")
				.param("mobile", mobile)
				.query((rs, row) -> new Member(rs.getObject("id", UUID.class), rs.getObject("society_id", UUID.class),
						rs.getObject("flat_id", UUID.class), Role.valueOf(rs.getString("role")), rs.getString("email"), rs.getString("name")))
				.optional();
	}

	Optional<Principal> findPrincipal(UUID memberId) {
		return jdbc.sql("select id, society_id, flat_id, role, name from members where id = :id")
				.param("id", memberId)
				.query((rs, row) -> new Principal(rs.getObject("id", UUID.class), rs.getObject("society_id", UUID.class),
						rs.getObject("flat_id", UUID.class), Role.valueOf(rs.getString("role")), rs.getString("name")))
				.optional();
	}
}
