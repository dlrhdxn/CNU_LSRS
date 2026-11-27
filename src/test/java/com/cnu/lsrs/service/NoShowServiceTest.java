package com.cnu.lsrs.service;

import com.cnu.lsrs.common.exception.BusinessException;
import com.cnu.lsrs.domain.CancelReason;
import com.cnu.lsrs.domain.NoShowRecord;
import com.cnu.lsrs.domain.NoShowSource;
import com.cnu.lsrs.domain.Reservation;
import com.cnu.lsrs.domain.ReservationPolicy;
import com.cnu.lsrs.domain.ReservationStatus;
import com.cnu.lsrs.domain.Role;
import com.cnu.lsrs.domain.Seat;
import com.cnu.lsrs.domain.User;
import com.cnu.lsrs.repository.NoShowRecordRepository;
import com.cnu.lsrs.repository.ReservationPolicyRepository;
import com.cnu.lsrs.repository.ReservationRepository;
import com.cnu.lsrs.repository.SeatRepository;
import com.cnu.lsrs.repository.UserRepository;
import com.cnu.lsrs.support.FixedClockConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static com.cnu.lsrs.support.FixedClockConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * US-09 미체크인 자동 취소·노쇼 기록, US-10 반복 노쇼 이용 제한의 Acceptance Criteria.
 * 시각은 2026-11-19 09:00으로 고정 (FixedClockConfig). 체크인 마감 = 시작 + 15분.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(FixedClockConfig.class)
class NoShowServiceTest {

	@Autowired NoShowService noShowService;
	@Autowired ReservationService reservationService;
	@Autowired ReservationRepository reservationRepository;
	@Autowired SeatRepository seatRepository;
	@Autowired UserRepository userRepository;
	@Autowired ReservationPolicyRepository policyRepository;
	@Autowired NoShowRecordRepository noShowRecordRepository;

	User student;
	User other;
	Seat a01;

	@BeforeEach
	void setUp() {
		noShowRecordRepository.deleteAll();
		reservationRepository.deleteAll();
		seatRepository.deleteAll();
		userRepository.deleteAll();
		policyRepository.deleteAll();

		policyRepository.save(ReservationPolicy.normal(7, 4));
		student = userRepository.save(User.createActive("20230001", "s1@lsrs.test", "hash", Role.STUDENT));
		other = userRepository.save(User.createActive("20230002", "s2@lsrs.test", "hash", Role.STUDENT));
		a01 = seatRepository.save(Seat.create("A-01", "1층"));
	}

	private Reservation saveReservation(User user, LocalDateTime startAt) {
		return reservationRepository.save(Reservation.create(user, a01, startAt, startAt.plusHours(2),
				UUID.randomUUID().toString(), startAt.minusDays(1)));
	}

	private ReservationStatus statusOf(Reservation reservation) {
		return reservationRepository.findById(reservation.getId()).orElseThrow().getStatus();
	}

	private User reload(User user) {
		return userRepository.findById(user.getId()).orElseThrow();
	}

	// ---- US-09 ----

	@Test
	void 체크인_마감이_지난_미체크인_예약은_자동_취소되고_노쇼로_기록된다() {
		Reservation late = saveReservation(student, NOW.minusMinutes(16)); // 마감 08:59 < 09:00

		int count = noShowService.processNoShows();

		assertThat(count).isEqualTo(1);
		assertThat(statusOf(late)).isEqualTo(ReservationStatus.AUTO_CANCELLED);
		assertThat(reload(student).getNoShowCount()).isEqualTo(1);
		List<NoShowRecord> records = noShowRecordRepository.findAll();
		assertThat(records).hasSize(1);
		assertThat(records.get(0).getSource()).isEqualTo(NoShowSource.AUTO);
		assertThat(records.get(0).getOccurredAt()).isEqualTo(NOW);
	}

	@Test
	void 체크인_마감_시각_정각에는_아직_취소하지_않는다() {
		// 시작 08:45 → 마감 09:00 = 지금. CheckInService도 이 시각까지 체크인을 허용한다.
		Reservation boundary = saveReservation(student, NOW.minusMinutes(15));

		assertThat(noShowService.processNoShows()).isZero();
		assertThat(statusOf(boundary)).isEqualTo(ReservationStatus.RESERVED);
		assertThat(reload(student).getNoShowCount()).isZero();
	}

	@Test
	void 체크인했거나_직접_취소한_예약은_노쇼가_아니다() {
		Reservation checkedIn = saveReservation(student, NOW.minusHours(1));
		checkedIn.checkIn(NOW.minusHours(1).plusMinutes(5));
		reservationRepository.save(checkedIn);
		Reservation cancelled = saveReservation(other, NOW.minusHours(1).plusMinutes(30));
		cancelled.cancelByStudent(CancelReason.SCHEDULE_CHANGE);
		reservationRepository.save(cancelled);

		assertThat(noShowService.processNoShows()).isZero();
		assertThat(statusOf(checkedIn)).isEqualTo(ReservationStatus.CHECKED_IN);
		assertThat(statusOf(cancelled)).isEqualTo(ReservationStatus.CANCELLED);
		assertThat(noShowRecordRepository.count()).isZero();
	}

	/*
	 * 참고: ReservationService.reserve()는 blocksSeatAt()으로 마감 지난 RESERVED도 이미 비켜주므로
	 * 재예약 자체는 스케줄러 전에도 된다. 여기서는 AUTO_CANCELLED로 바뀐 뒤에도 재예약되는지 본다.
	 */
	@Test
	void 자동_취소된_좌석은_다른_학생이_다시_예약할_수_있다() {
		Reservation noShow = saveReservation(student, NOW.minusMinutes(30)); // 08:30~10:30

		noShowService.processNoShows();
		assertThat(statusOf(noShow)).isEqualTo(ReservationStatus.AUTO_CANCELLED);

		var result = reservationService.reserve(other.getId(), a01.getId(), NOW.toLocalDate(),
				LocalTime.of(9, 30), LocalTime.of(10, 30));
		assertThat(result.status()).isEqualTo(ReservationStatus.RESERVED);
	}

	@Test
	void 스케줄러가_다시_실행돼도_같은_예약을_두_번_세지_않는다() {
		saveReservation(student, NOW.minusMinutes(20));

		noShowService.processNoShows();
		assertThat(noShowService.processNoShows()).isZero();

		assertThat(reload(student).getNoShowCount()).isEqualTo(1);
		assertThat(noShowRecordRepository.count()).isEqualTo(1);
	}

	// ---- US-10 ----

	@Test
	void 노쇼_3회째에_3일_이용_제한이_걸리고_예약이_거부된다() {
		saveReservation(student, NOW.minusHours(5));
		saveReservation(student, NOW.minusHours(3));
		saveReservation(student, NOW.minusMinutes(30));

		assertThat(noShowService.processNoShows()).isEqualTo(3);

		User restricted = reload(student);
		assertThat(restricted.getRestrictionUntil()).isEqualTo(NOW.plus(Duration.ofDays(3)));
		assertThat(restricted.isRestricted(NOW)).isTrue();
		assertThat(restricted.getNoShowCount()).isZero(); // 제한 시작 시 0으로 초기화 (설계서 Rule)
		assertThatThrownBy(() -> reservationService.reserve(student.getId(), a01.getId(),
				NOW.toLocalDate().plusDays(1), LocalTime.of(10, 0), LocalTime.of(11, 0)))
				.isInstanceOf(BusinessException.class)
				.extracting("code").isEqualTo("RESERVATION_RESTRICTED");
	}

	@Test
	void 노쇼_2회까지는_제한되지_않는다() {
		saveReservation(student, NOW.minusHours(3));
		saveReservation(student, NOW.minusMinutes(30));

		noShowService.processNoShows();

		User user = reload(student);
		assertThat(user.getNoShowCount()).isEqualTo(2);
		assertThat(user.isRestricted(NOW)).isFalse();
	}

	@Test
	void 이용_제한은_기간이_지나면_자동으로_풀린다() {
		saveReservation(student, NOW.minusHours(5));
		saveReservation(student, NOW.minusHours(3));
		saveReservation(student, NOW.minusMinutes(30));
		noShowService.processNoShows();

		User user = reload(student);
		assertThat(user.isRestricted(NOW.plusDays(3).minusSeconds(1))).isTrue();
		assertThat(user.isRestricted(NOW.plusDays(3))).isFalse();
	}
}
