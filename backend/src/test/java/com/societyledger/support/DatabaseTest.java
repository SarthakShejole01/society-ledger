package com.societyledger.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;

/** Marks a test that needs the real application context on a real PostgreSQL. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@Import({TestClockConfig.class, TestAuthConfig.class})
@ContextConfiguration(initializers = DatabaseTestProperties.class)
public @interface DatabaseTest {
}
