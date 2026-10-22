package com.societyledger.common;

/**
 * An amount of money in whole paise. Never negative.
 */
public record Money(long paise) {

	public static final Money ZERO = new Money(0);

	public Money {
		if (paise < 0) {
			throw new IllegalArgumentException("Money cannot be negative: " + paise);
		}
	}

	public static Money ofPaise(long paise) {
		return new Money(paise);
	}

	public static Money rupees(long rupees) {
		return new Money(Math.multiplyExact(rupees, 100L));
	}

	public Money plus(Money other) {
		return new Money(Math.addExact(paise, other.paise));
	}

	public Money times(int multiplier) {
		if (multiplier < 0) {
			throw new IllegalArgumentException("Cannot multiply money by a negative number: " + multiplier);
		}
		return new Money(Math.multiplyExact(paise, (long) multiplier));
	}

	/** For messages and logs, for example "Rs 1600.50". Not for calculation. */
	public String formatted() {
		return "Rs %d.%02d".formatted(paise / 100, paise % 100);
	}
}
