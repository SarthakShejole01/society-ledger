package com.societyledger.auth;

public record AuthSession(String token, Principal principal) {
}
