package com.societyledger.billing;

import com.societyledger.common.Money;

/** A flat that has not paid, with what it owes if it pays right now. */
public record PendingFlat(String number, int floor, Money dueNow) {
}
