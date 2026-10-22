package com.societyledger.billing;

import java.util.UUID;

/**
 * Whether a flat has paid for a month. Carries no amounts, so it is safe for every owner to see.
 * The id is what lets one member start a payment for another flat.
 */
public record FlatStatus(UUID flatId, String number, int floor, boolean paid) {
}
