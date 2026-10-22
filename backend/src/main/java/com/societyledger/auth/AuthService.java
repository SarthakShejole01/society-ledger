package com.societyledger.auth;

import com.societyledger.common.tenant.SocietyScope;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Passwordless login: a member asks for a one-time code, it is emailed to the address on their record,
 * and the right code exchanges for a session token.
 */
@Service
public class AuthService {

	private static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
	private static final Duration REQUEST_WINDOW = Duration.ofMinutes(10);
	private static final int MAX_REQUESTS_IN_WINDOW = 3;
	private static final int MAX_WRONG_ATTEMPTS = 5;
	private static final Duration SESSION_LIFETIME = Duration.ofHours(12);

	private final SocietyScope scope;
	private final SocietyDirectory societies;
	private final MemberRepository members;
	private final OtpRepository otps;
	private final SessionRepository sessions;
	private final OtpCodeGenerator codes;
	private final OtpSender sender;
	private final SecretHasher hasher;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	public AuthService(SocietyScope scope, SocietyDirectory societies, MemberRepository members, OtpRepository otps,
			SessionRepository sessions, OtpCodeGenerator codes, OtpSender sender, SecretHasher hasher, Clock clock) {
		this.scope = scope;
		this.societies = societies;
		this.members = members;
		this.otps = otps;
		this.sessions = sessions;
		this.codes = codes;
		this.sender = sender;
		this.hasher = hasher;
		this.clock = clock;
	}

	/** Sends a code if the number is registered. Says nothing either way, so numbers cannot be guessed. */
	public void requestOtp(String societyCode, String mobile) {
		Optional<UUID> societyId = societies.idForCode(societyCode);
		if (societyId.isEmpty()) {
			return;
		}
		boolean tooMany = scope.inSociety(societyId.get(), () -> {
			Optional<MemberRepository.Member> member = members.findByMobile(mobile);
			if (member.isEmpty()) {
				return false;
			}
			Instant now = Instant.now(clock);
			if (otps.countRequestsSince(member.get().id(), now.minus(REQUEST_WINDOW)) >= MAX_REQUESTS_IN_WINDOW) {
				return true;
			}
			otps.invalidateActive(member.get().id());
			String code = codes.next();
			otps.insert(societyId.get(), member.get().id(), codeHash(member.get().id(), code), now, now.plus(CODE_LIFETIME));
			sender.send(member.get().email(), code);
			return false;
		});
		if (tooMany) {
			throw new TooManyOtpRequestsException();
		}
	}

	public AuthSession verifyOtp(String societyCode, String mobile, String code) {
		UUID societyId = societies.idForCode(societyCode).orElseThrow(InvalidOtpException::new);
		// Failed attempts must be saved, so the outcome is returned and the exception thrown after commit.
		Optional<AuthSession> session = scope.inSociety(societyId, () -> {
			Optional<MemberRepository.Member> member = members.findByMobile(mobile);
			if (member.isEmpty()) {
				return Optional.<AuthSession>empty();
			}
			Instant now = Instant.now(clock);
			Optional<OtpRepository.Challenge> challenge = otps.latestActive(member.get().id(), now);
			if (challenge.isEmpty() || challenge.get().attempts() >= MAX_WRONG_ATTEMPTS) {
				return Optional.<AuthSession>empty();
			}
			boolean matches = MessageDigest.isEqual(
					challenge.get().codeHash().getBytes(StandardCharsets.UTF_8),
					codeHash(member.get().id(), code).getBytes(StandardCharsets.UTF_8));
			if (!matches) {
				otps.recordFailedAttempt(challenge.get().id());
				return Optional.<AuthSession>empty();
			}
			otps.markUsed(challenge.get().id());
			String token = societyId + "." + randomToken();
			sessions.insert(hasher.hash(token), societyId, member.get().id(), now, now.plus(SESSION_LIFETIME));
			Principal principal = new Principal(member.get().id(), societyId, member.get().flatId(), member.get().role(), member.get().name());
			return Optional.of(new AuthSession(token, principal));
		});
		return session.orElseThrow(InvalidOtpException::new);
	}

	/** The member behind a session token, or empty if the token is unknown, forged or expired. */
	public Optional<Principal> authenticate(String token) {
		int dot = token.indexOf('.');
		if (dot < 0) {
			return Optional.empty();
		}
		UUID societyId;
		try {
			societyId = UUID.fromString(token.substring(0, dot));
		}
		catch (IllegalArgumentException e) {
			return Optional.empty();
		}
		return scope.inSociety(societyId, () -> sessions.memberFor(hasher.hash(token), Instant.now(clock))
				.flatMap(members::findPrincipal));
	}

	private String codeHash(UUID memberId, String code) {
		return hasher.hash(memberId + ":" + code);
	}

	private String randomToken() {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
