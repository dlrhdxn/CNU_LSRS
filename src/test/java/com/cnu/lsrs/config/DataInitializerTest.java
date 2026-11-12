package com.cnu.lsrs.config;

import com.cnu.lsrs.domain.ReservationStatus;
import com.cnu.lsrs.domain.SeatStatus;
import com.cnu.lsrs.repository.ReservationPolicyRepository;
import com.cnu.lsrs.repository.ReservationRepository;
import com.cnu.lsrs.repository.SeatRepository;
import com.cnu.lsrs.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** 개발용 초기 데이터가 오류 없이 들어가는지 확인 */
@SpringBootTest(properties = "lsrs.seed.enabled=true")
@ActiveProfiles("test")
class DataInitializerTest {

	@Autowired
	UserRepository userRepository;

	@Autowired
	SeatRepository seatRepository;

	@Autowired
	ReservationRepository reservationRepository;

	@Autowired
	ReservationPolicyRepository policyRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Test
	void 초기_데이터가_생성된다() {
		assertThat(userRepository.count()).isEqualTo(5);
		assertThat(seatRepository.count()).isEqualTo(20);
		assertThat(policyRepository.count()).isEqualTo(1);
		assertThat(reservationRepository.count()).isEqualTo(3);

		assertThat(seatRepository.findBySeatCode("B-08").orElseThrow().getStatus()).isEqualTo(SeatStatus.UNAVAILABLE);
		assertThat(reservationRepository.findAll())
				.extracting(r -> r.getStatus())
				.containsExactlyInAnyOrder(ReservationStatus.RESERVED, ReservationStatus.RESERVED,
						ReservationStatus.CHECKED_IN);

		String hash = userRepository.findByStudentNo("20230001").orElseThrow().getPasswordHash();
		assertThat(passwordEncoder.matches(DataInitializer.TEST_PASSWORD, hash)).isTrue();
	}
}
