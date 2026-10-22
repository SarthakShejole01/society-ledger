package com.societyledger.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SecretHasherTest {

	@Test
	void refuses_a_secret_shorter_than_sixteen_characters() {
		assertThatThrownBy(() -> new SecretHasher("short")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void refuses_the_placeholder_secret_from_the_example_config() {
		assertThatThrownBy(() -> new SecretHasher("change-me-to-a-long-random-string"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("placeholder");
	}

	@Test
	void the_same_value_always_hashes_the_same_and_a_different_secret_gives_a_different_hash() {
		SecretHasher a = new SecretHasher("a-real-secret-0123456789");
		SecretHasher b = new SecretHasher("another-real-secret-0123");

		assertThat(a.hash("123456")).isEqualTo(a.hash("123456")).isNotEqualTo(b.hash("123456"));
	}
}
