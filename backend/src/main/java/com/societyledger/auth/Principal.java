package com.societyledger.auth;

import java.util.UUID;

/** Who is making a request: a member of one society. */
public record Principal(UUID memberId, UUID societyId, UUID flatId, Role role) {
}
