package com.societyledger.billing;

import com.societyledger.common.Money;
import java.time.YearMonth;

/** What a flat owes for a month, as of now. */
public record Dues(String flatNumber, YearMonth month, Money amount, Money lateFee, Money total, boolean paid) {
}
