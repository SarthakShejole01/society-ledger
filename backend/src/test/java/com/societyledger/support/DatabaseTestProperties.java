package com.societyledger.support;

import java.util.Map;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

/** Points the application at the test PostgreSQL: the app role for queries, the admin for migrations. */
public class DatabaseTestProperties implements ApplicationContextInitializer<ConfigurableApplicationContext> {

	@Override
	public void initialize(ConfigurableApplicationContext context) {
		context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("database-test", Map.of(
				"spring.datasource.url", PostgresTestSupport.url(),
				"spring.datasource.username", PostgresTestSupport.APP_USER,
				"spring.datasource.password", PostgresTestSupport.APP_PASSWORD,
				"spring.flyway.url", PostgresTestSupport.url(),
				"spring.flyway.user", PostgresTestSupport.adminUser(),
				"spring.flyway.password", PostgresTestSupport.adminPassword(),
				"society-ledger.auth.secret", "test-secret-not-for-production")));
	}
}
