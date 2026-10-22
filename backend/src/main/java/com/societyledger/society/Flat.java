package com.societyledger.society;

import com.societyledger.common.Money;
import java.util.UUID;

public record Flat(UUID id, UUID societyId, String number, int floor, Money monthlyAmount) {
}
