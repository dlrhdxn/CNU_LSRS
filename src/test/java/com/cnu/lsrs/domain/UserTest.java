package com.cnu.lsrs.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

	@Test
	void 가입한_학생은_PENDING이고_인증하면_ACTIVE가_된다() {
		User user = User.createPendingStudent("20230009", "s9@lsrs.test", "hash", "123456");
		assertThat(user.isActive()).isFalse();
		assertThat(user.getRole()).isEqualTo(Role.STUDENT);

		user.activate();

		assertThat(user.isActive()).isTrue();
		assertThat(user.getVerificationCode()).isNull();
	}

	@Test
	void 제한_해제_시각이_없으면_제한되지_않는다() {
		User user = User.createActive("20230001", "s1@lsrs.test", "hash", Role.STUDENT);
		assertThat(user.isRestricted(java.time.LocalDateTime.now())).isFalse();
		assertThat(user.getNoShowCount()).isZero();
	}
}
