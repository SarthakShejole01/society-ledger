package com.societyledger.billing;

import com.societyledger.common.Money;
import java.util.UUID;

/** A flat that has not paid: what it owes before any late fee, and what it owes if it pays right now. */
public record PendingFlat(UUID flatId, String number, int floor, Money amount, Money dueNow) {
}
