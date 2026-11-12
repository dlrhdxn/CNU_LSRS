package com.cnu.lsrs.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 체크인 QR용 토큰 생성기 (FR-C-01, US-07).
 * SecureRandom 32바이트(256비트)를 URL-safe Base64로 만든 43자 문자열이라 추측할 수 없다.
 * 예약 생성(US-04) 시 ReservationService가 호출한다.
 */
@Component
public class QrTokenGenerator {

	private static final int TOKEN_BYTES = 32;

	private final SecureRandom random = new SecureRandom();

	public String generate() {
		byte[] bytes = new byte[TOKEN_BYTES];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
