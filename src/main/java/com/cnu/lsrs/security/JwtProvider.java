package com.cnu.lsrs.security;

import com.cnu.lsrs.config.LsrsProperties;
import com.cnu.lsrs.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtProvider {

	private final SecretKey key;
	private final LsrsProperties properties;
	private final Clock clock;

	public JwtProvider(LsrsProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
		this.key = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
	}

	public String createAccessToken(Long userId, String loginId, Role role) {
		return createToken(userId, loginId, role, properties.jwt().accessTokenTtl().toMillis(), "access");
	}

	public String createRefreshToken(Long userId, String loginId, Role role) {
		return createToken(userId, loginId, role, properties.jwt().refreshTokenTtl().toMillis(), "refresh");
	}

	private String createToken(Long userId, String loginId, Role role, long ttlMillis, String tokenType) {
		Instant now = clock.instant();
		return Jwts.builder()
				.subject(String.valueOf(userId))
				.claim("loginId", loginId)
				.claim("role", role.name())
				.claim("type", tokenType)
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plusMillis(ttlMillis)))
				.signWith(key)
				.compact();
	}

	public Claims parse(String token) {
		return Jwts.parser()
				.verifyWith(key)
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}
}
