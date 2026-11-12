package com.cnu.lsrs.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class QrTokenGeneratorTest {

	private final QrTokenGenerator generator = new QrTokenGenerator();

	@Test
	void 토큰은_URL에_안전한_43자_문자열이다() {
		String token = generator.generate();

		assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
	}

	@Test
	void 토큰은_매번_다르다() {
		Set<String> tokens = new HashSet<>();
		for (int i = 0; i < 1000; i++) {
			tokens.add(generator.generate());
		}
		assertThat(tokens).hasSize(1000);
	}
}
