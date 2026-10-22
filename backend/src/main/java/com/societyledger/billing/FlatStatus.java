package com.societyledger.billing;

/** Whether a flat has paid for a month. Carries no amounts, so it is safe for every owner to see. */
public record FlatStatus(String number, int floor, boolean paid) {
}
