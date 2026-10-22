package com.societyledger.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A real PostgreSQL for tests. Uses the database named by SOCIETY_LEDGER_TEST_DB_URL when set
 * (for machines without Docker), otherwise starts a Testcontainers PostgreSQL.
 *
 * <p>Two roles are used: an admin that owns the schema and runs migrations, and a plain
 * application role that is subject to row-level security, as in production.
 */
public final class PostgresTestSupport {

	public static final String APP_USER = "ledger_app";
	public static final String APP_PASSWORD = "ledger_app_test";

	private static final String LOCAL_URL = System.getenv("SOCIETY_LEDGER_TEST_DB_URL");
	private static final String LOCAL_USER = envOr("SOCIETY_LEDGER_TEST_DB_USER", "postgres");
	private static final String LOCAL_PASSWORD = envOr("SOCIETY_LEDGER_TEST_DB_PASSWORD", "postgres");

	private static final PostgreSQLContainer CONTAINER = LOCAL_URL == null
			? new PostgreSQLContainer("postgres:16-alpine")
			: null;

	static {
		if (CONTAINER != null) {
			CONTAINER.start();
		}
		ensureAppRole();
	}

	private PostgresTestSupport() {
	}

	public static String url() {
		return CONTAINER != null ? CONTAINER.getJdbcUrl() : LOCAL_URL;
	}

	public static String adminUser() {
		return CONTAINER != null ? CONTAINER.getUsername() : LOCAL_USER;
	}

	public static String adminPassword() {
		return CONTAINER != null ? CONTAINER.getPassword() : LOCAL_PASSWORD;
	}

	/** A connection with the schema owner's rights, used to seed and clean test data. */
	public static JdbcTemplate admin() {
		return new JdbcTemplate(new DriverManagerDataSource(url(), adminUser(), adminPassword()));
	}

	private static void ensureAppRole() {
		try (Connection connection = DriverManager.getConnection(url(), adminUser(), adminPassword());
				Statement statement = connection.createStatement()) {
			statement.execute("""
					do $$ begin
					  if not exists (select 1 from pg_roles where rolname = '%s') then
					    create role %s login password '%s';
					  end if;
					end $$""".formatted(APP_USER, APP_USER, APP_PASSWORD));
		}
		catch (SQLException e) {
			throw new IllegalStateException("Cannot prepare the test database role", e);
		}
	}

	private static String envOr(String name, String fallback) {
		String value = System.getenv(name);
		return value == null ? fallback : value;
	}
}
