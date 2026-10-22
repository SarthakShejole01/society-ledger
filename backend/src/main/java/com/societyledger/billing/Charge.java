package com.societyledger.billing;

import com.societyledger.common.Money;
import java.time.YearMonth;
import java.util.UUID;

public record Charge(UUID id, UUID flatId, YearMonth month, Money amount) {
}
