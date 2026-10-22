package com.societyledger.auth;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Keyed hash for codes and tokens, so a copy of the database alone cannot reveal them. */
@Component
class SecretHasher {

	private final byte[] key;

	SecretHasher(@Value("${society-ledger.auth.secret}") String secret) {
		if (secret.length() < 16) {
			throw new IllegalStateException("society-ledger.auth.secret must be at least 16 characters");
		}
		this.key = secret.getBytes(StandardCharsets.UTF_8);
	}

	String hash(String value) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(key, "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException(e);
		}
	}
}
