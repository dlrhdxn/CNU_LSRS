package com.cnu.lsrs.config;

import com.cnu.lsrs.domain.ReservationStatus;
import com.cnu.lsrs.domain.Reservation;
import com.cnu.lsrs.domain.User;
import com.cnu.lsrs.domain.SeatStatus;
import com.cnu.lsrs.repository.ReservationPolicyRepository;
import com.cnu.lsrs.repository.ReservationRepository;
import com.cnu.lsrs.repository.SeatRepository;
import com.cnu.lsrs.repository.UserRepository;
import com.cnu.lsrs.service.ReservationService;
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

	@Autowired
	ReservationService reservationService;

	@Test
	void 초기_데이터가_생성된다() {
		assertThat(userRepository.count()).isEqualTo(9);
		assertThat(seatRepository.count()).isEqualTo(20);
		assertThat(policyRepository.count()).isEqualTo(1);
		assertThat(reservationRepository.count()).isEqualTo(7);

		assertThat(seatRepository.findBySeatCode("B-08").orElseThrow().getStatus()).isEqualTo(SeatStatus.UNAVAILABLE);
		assertThat(userRepository.findByStudentNo("20240001")).isPresent();
		assertThat(reservationRepository.findAllByUser_IdOrderByCreatedAtDesc(
				userRepository.findByStudentNo("20240001").orElseThrow().getId())).isEmpty();
		assertThat(reservationFor("20240002").getStatus()).isEqualTo(ReservationStatus.RESERVED);
		assertThat(reservationFor("20240003").getStatus()).isEqualTo(ReservationStatus.RESERVED);
		assertThat(reservationFor("20240004").getStatus()).isEqualTo(ReservationStatus.RESERVED);
		assertThat(myReservation("20240002").qrAvailable()).isFalse();
		assertThat(myReservation("20240003").qrAvailable()).isTrue();
		assertThat(myReservation("20240004").qrAvailable()).isFalse();

		String hash = userRepository.findByStudentNo("20230001").orElseThrow().getPasswordHash();
		assertThat(passwordEncoder.matches(DataInitializer.TEST_PASSWORD, hash)).isTrue();
		for (String loginId : new String[]{"20240001", "20240002", "20240003", "20240004"}) {
			User user = userRepository.findByStudentNo(loginId).orElseThrow();
			assertThat(passwordEncoder.matches(DataInitializer.TEST_PASSWORD, user.getPasswordHash())).isTrue();
		}
	}

	private Reservation reservationFor(String loginId) {
		Long userId = userRepository.findByStudentNo(loginId).orElseThrow().getId();
		return reservationRepository.findAllByUser_IdOrderByCreatedAtDesc(userId).get(0);
	}

	private ReservationService.MyReservation myReservation(String loginId) {
		Long userId = userRepository.findByStudentNo(loginId).orElseThrow().getId();
		return reservationService.getMyReservations(userId).get(0);
	}
}
