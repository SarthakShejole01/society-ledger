package com.societyledger.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.societyledger.common.tenant.SocietyScope;
import com.societyledger.support.DatabaseTest;
import com.societyledger.support.PostgresTestSupport;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@DatabaseTest
class AuditLogTest {

	private static final UUID SUNRISE = UUID.fromString("00000000-0000-0000-0000-00000000000a");

	@Autowired
	private AuditLog audit;
	@Autowired
	private SocietyScope scope;

	private final JdbcTemplate admin = PostgresTestSupport.admin();

	@BeforeEach
	void aSocietyWithAnEmptyLog() {
		admin.execute("truncate audit_log, sessions, otp_challenges, members, payments, charges, flats, societies cascade");
		admin.update("insert into societies (id, code, name) values (?, 'sunrise', 'Sunrise Apartments')", SUNRISE);
	}

	@Test
	void an_entry_can_be_added() {
		scope.inSociety(SUNRISE, () -> {
			audit.record(SUNRISE, null, "TEST", "hello");
			return null;
		});

		assertThat(admin.queryForObject("select count(*) from audit_log", Integer.class)).isEqualTo(1);
	}

	@Test
	void the_application_cannot_change_or_remove_entries() throws SQLException {
		scope.inSociety(SUNRISE, () -> {
			audit.record(SUNRISE, null, "TEST", "hello");
			return null;
		});

		try (Connection app = DriverManager.getConnection(PostgresTestSupport.url(), PostgresTestSupport.APP_USER,
				PostgresTestSupport.APP_PASSWORD);
				Statement statement = app.createStatement()) {
			assertThatThrownBy(() -> statement.execute("update audit_log set detail = 'tampered'"))
					.isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
			assertThatThrownBy(() -> statement.execute("delete from audit_log"))
					.isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
		}
	}
}
