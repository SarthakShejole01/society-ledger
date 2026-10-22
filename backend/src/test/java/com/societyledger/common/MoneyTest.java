package com.societyledger.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTest {

	@Test
	void rupees_are_stored_as_paise() {
		assertThat(Money.rupees(1500).paise()).isEqualTo(150_000);
	}

	@Test
	void rejects_negative_amount() {
		assertThatThrownBy(() -> Money.ofPaise(-1))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void adds_two_amounts() {
		assertThat(Money.rupees(1500).plus(Money.rupees(100))).isEqualTo(Money.rupees(1600));
	}

	@Test
	void multiplies_by_a_whole_number_of_months() {
		assertThat(Money.rupees(1500).times(3)).isEqualTo(Money.rupees(4500));
	}

	@Test
	void zero_is_zero_paise() {
		assertThat(Money.ZERO.paise()).isZero();
	}

	@Test
	void rejects_multiplying_by_a_negative_number() {
		assertThatThrownBy(() -> Money.rupees(1500).times(-1))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
