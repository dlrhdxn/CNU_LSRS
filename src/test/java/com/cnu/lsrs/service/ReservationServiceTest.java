package com.cnu.lsrs.service;

import com.cnu.lsrs.common.exception.BusinessException;
import com.cnu.lsrs.domain.CancelReason;
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
import com.cnu.lsrs.service.dto.ReservationResult;
import com.cnu.lsrs.service.dto.SeatAvailability;
import com.cnu.lsrs.service.dto.SeatView;
import com.cnu.lsrs.service.dto.SeatView.SeatState;
import com.cnu.lsrs.support.FixedClockConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.cnu.lsrs.support.FixedClockConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * US-03 좌석 조회, US-04 좌석 예약, US-05 예약 취소의 Acceptance Criteria.
 * 시각은 2026-11-19 09:00으로 고정 (FixedClockConfig).
 * 동시성 테스트가 실제 커밋을 해야 하므로 @Transactional 없이 매번 데이터를 지우고 다시 만든다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(FixedClockConfig.class)
class ReservationServiceTest {

	private static final LocalDate TODAY = NOW.toLocalDate();

	@Autowired ReservationService reservationService;
	@Autowired SeatService seatService;
	@Autowired ReservationRepository reservationRepository;
	@Autowired SeatRepository seatRepository;
	@Autowired UserRepository userRepository;
	@Autowired ReservationPolicyRepository policyRepository;
	@Autowired NoShowRecordRepository noShowRecordRepository;

	User student1;
	User student2;
	Seat a01;
	Seat a02;
	Seat closed;

	@BeforeEach
	void setUp() {
		noShowRecordRepository.deleteAll();
		reservationRepository.deleteAll();
		seatRepository.deleteAll();
		userRepository.deleteAll();
		policyRepository.deleteAll();

		policyRepository.save(ReservationPolicy.normal(7, 4));
		student1 = userRepository.save(User.createActive("20230001", "s1@lsrs.test", "hash", Role.STUDENT));
		student2 = userRepository.save(User.createActive("20230002", "s2@lsrs.test", "hash", Role.STUDENT));
		a01 = seatRepository.save(Seat.create("A-01", "1층"));
		a02 = seatRepository.save(Seat.create("A-02", "1층"));
		Seat b08 = Seat.create("B-08", "2층");
		b08.markUnavailable();
		closed = seatRepository.save(b08);
	}

	private ReservationResult reserve(User user, Seat seat, String start, String end) {
		return reservationService.reserve(user.getId(), seat.getId(), TODAY, LocalTime.parse(start), LocalTime.parse(end));
	}

	private String errorCode(Runnable action) {
		try {
			action.run();
		} catch (BusinessException e) {
			return e.getCode();
		}
		return null;
	}

	private SeatState stateOf(SeatAvailability result, Seat seat) {
		return result.seats().stream().filter(s -> s.seatId().equals(seat.getId()))
				.map(SeatView::state).findFirst().orElseThrow();
	}

	// ---------- US-04 좌석 예약 ----------

	@Test
	void US04_예약하면_RESERVED로_확정되고_QR_토큰이_발급된다() {
		ReservationResult result = reserve(student1, a01, "10:00", "12:00");

		assertThat(result.status()).isEqualTo(ReservationStatus.RESERVED);
		assertThat(result.qrToken()).hasSize(43);
		assertThat(reservationRepository.findById(result.reservationId())).isPresent();
	}

	@Test
	void US04_이미_예약된_좌석의_겹치는_시간대는_거부된다() {
		reserve(student1, a01, "10:00", "12:00");

		assertThat(errorCode(() -> reserve(student2, a01, "11:00", "13:00"))).isEqualTo("SEAT_ALREADY_RESERVED");
	}

	@Test
	void US04_앞_예약이_끝나는_시각에_시작하는_예약은_허용된다() {
		reserve(student1, a01, "10:00", "12:00");

		assertThat(reserve(student2, a01, "12:00", "14:00").status()).isEqualTo(ReservationStatus.RESERVED);
	}

	@Test
	void US04_사용_불가_좌석은_거부된다() {
		assertThat(errorCode(() -> reserve(student1, closed, "10:00", "12:00"))).isEqualTo("SEAT_UNAVAILABLE");
	}

	@Test
	void US04_같은_학생이_겹치는_시간대에_다른_좌석을_예약하면_거부된다() {
		reserve(student1, a01, "10:00", "12:00");

		assertThat(errorCode(() -> reserve(student1, a02, "11:30", "13:00"))).isEqualTo("USER_TIME_OVERLAP");
	}

	@Test
	void US04_같은_학생이_앞_예약이_끝나는_시각에_다른_좌석을_이어서_예약하는_것은_허용된다() {
		reserve(student1, a01, "10:00", "12:00");

		assertThat(reserve(student1, a02, "12:00", "13:00").status()).isEqualTo(ReservationStatus.RESERVED);
	}

	@Test
	void US04_정책을_위반한_요청은_거부된다() {
		assertThat(errorCode(() -> reserve(student1, a01, "10:00", "15:00"))).isEqualTo("EXCEEDS_MAX_HOURS");
	}

	@Test
	void US04_이용_제한_중인_학생은_거부되고_해제일이_안내된다() {
		User restricted = userRepository.findById(student1.getId()).orElseThrow();
		ReflectionTestUtils.setField(restricted, "restrictionUntil", NOW.plusDays(2)); // 노쇼 제한 기능(US-10)은 Sprint 2
		userRepository.save(restricted);

		assertThatThrownBy(() -> reserve(student1, a01, "10:00", "12:00"))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("2026-11-21 09:00")
				.extracting("code").isEqualTo("RESERVATION_RESTRICTED");
	}

	@Test
	void US04_같은_좌석_같은_시간대_동시_요청은_하나만_확정된다() throws Exception {
		int threads = 10;
		List<User> students = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			students.add(userRepository.save(
					User.createActive("2024" + String.format("%04d", i), "c" + i + "@lsrs.test", "hash", Role.STUDENT)));
		}

		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> futures = new ArrayList<>();
		for (User s : students) {
			Callable<Boolean> task = () -> {
				start.await();
				try {
					reserve(s, a01, "10:00", "12:00");
					return true;
				} catch (BusinessException e) {
					return false;
				}
			};
			futures.add(pool.submit(task));
		}
		start.countDown();

		int success = 0;
		for (Future<Boolean> f : futures) {
			if (f.get()) {
				success++;
			}
		}
		pool.shutdown();

		assertThat(success).isEqualTo(1);
		assertThat(reservationRepository.findAll()).hasSize(1);
	}

	/** FR-R-11: 좌석이 다 달라서 좌석 잠금으로는 못 막는 경우. 학생 행 잠금이 없으면 여러 건이 확정된다 */
	@Test
	void US04_같은_학생이_다른_좌석으로_동시에_요청해도_하나만_확정된다() throws Exception {
		int threads = 10;
		List<Seat> seats = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			seats.add(seatRepository.save(Seat.create("C-" + String.format("%02d", i), "3층")));
		}

		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> futures = new ArrayList<>();
		for (Seat seat : seats) {
			Callable<Boolean> task = () -> {
				start.await();
				try {
					reserve(student1, seat, "10:00", "12:00");
					return true;
				} catch (BusinessException e) {
					return false;
				}
			};
			futures.add(pool.submit(task));
		}
		start.countDown();

		int success = 0;
		for (Future<Boolean> f : futures) {
			if (f.get()) {
				success++;
			}
		}
		pool.shutdown();

		assertThat(success).isEqualTo(1);
		assertThat(reservationRepository.findAll()).hasSize(1);
	}

	// ---------- US-05 예약 취소 ----------

	@Test
	void US05_취소하면_CANCELLED가_되고_노쇼로_기록되지_않는다() {
		ReservationResult r = reserve(student1, a01, "10:00", "12:00");

		ReservationResult cancelled = reservationService.cancel(student1.getId(), r.reservationId(), CancelReason.SCHEDULE_CHANGE);

		assertThat(cancelled.status()).isEqualTo(ReservationStatus.CANCELLED);
		assertThat(cancelled.cancelReason()).isEqualTo(CancelReason.SCHEDULE_CHANGE);
		assertThat(noShowRecordRepository.count()).isZero();
		assertThat(userRepository.findById(student1.getId()).orElseThrow().getNoShowCount()).isZero();
	}

	@Test
	void US05_취소된_좌석은_다른_학생이_바로_예약할_수_있다() {
		ReservationResult r = reserve(student1, a01, "10:00", "12:00");
		reservationService.cancel(student1.getId(), r.reservationId(), CancelReason.SEAT_CHANGE);

		assertThat(reserve(student2, a01, "10:00", "12:00").status()).isEqualTo(ReservationStatus.RESERVED);
	}

	@Test
	void US05_다른_학생의_예약은_취소할_수_없다() {
		ReservationResult r = reserve(student1, a01, "10:00", "12:00");

		assertThat(errorCode(() -> reservationService.cancel(student2.getId(), r.reservationId(), CancelReason.STUDENT_OTHER)))
				.isEqualTo("RESERVATION_FORBIDDEN");
	}

	@Test
	void US05_체크인한_예약과_이미_취소한_예약은_취소할_수_없다() {
		ReservationResult r = reserve(student1, a01, "10:00", "12:00");
		Reservation reservation = reservationRepository.findById(r.reservationId()).orElseThrow();
		reservation.checkIn(NOW);
		reservationRepository.save(reservation);

		assertThat(errorCode(() -> reservationService.cancel(student1.getId(), r.reservationId(), CancelReason.STUDENT_OTHER)))
				.isEqualTo("RESERVATION_NOT_CANCELLABLE");

		ReservationResult r2 = reserve(student1, a02, "13:00", "14:00");
		reservationService.cancel(student1.getId(), r2.reservationId(), CancelReason.STUDENT_OTHER);
		assertThat(errorCode(() -> reservationService.cancel(student1.getId(), r2.reservationId(), CancelReason.STUDENT_OTHER)))
				.isEqualTo("RESERVATION_NOT_CANCELLABLE");
	}

	@Test
	void US05_체크인_마감_시각까지는_취소할_수_있다() {
		Reservation r = reservationRepository.save(Reservation.create(student1, a01,
				NOW.minusMinutes(15), NOW.plusHours(1), "token-deadline-edge", NOW.minusDays(1)));

		assertThat(reservationService.cancel(student1.getId(), r.getId(), CancelReason.STUDENT_OTHER).status())
				.isEqualTo(ReservationStatus.CANCELLED);
	}

	@Test
	void US05_체크인_마감이_지나면_취소할_수_없다() {
		Reservation r = reservationRepository.save(Reservation.create(student1, a01,
				NOW.minusMinutes(16), NOW.plusHours(1), "token-deadline-passed", NOW.minusDays(1)));

		assertThat(errorCode(() -> reservationService.cancel(student1.getId(), r.getId(), CancelReason.STUDENT_OTHER)))
				.isEqualTo("CANCEL_DEADLINE_PASSED");
		assertThat(reservationRepository.findById(r.getId()).orElseThrow().getStatus())
				.isEqualTo(ReservationStatus.RESERVED);
	}

	@Test
	void US05_관리자용_사유로는_취소할_수_없다() {
		ReservationResult r = reserve(student1, a01, "10:00", "12:00");

		assertThat(errorCode(() -> reservationService.cancel(student1.getId(), r.reservationId(), CancelReason.RULE_VIOLATION)))
				.isEqualTo("INVALID_CANCEL_REASON");
	}

	// ---------- US-03 좌석 조회 ----------

	@Test
	void US03_좌석은_예약가능_사용중_내예약_예약됨_사용불가로_구분된다() {
		Seat a03 = seatRepository.save(Seat.create("A-03", "1층"));
		Seat a04 = seatRepository.save(Seat.create("A-04", "1층"));
		ReservationResult mine = reserve(student1, a01, "10:00", "12:00");
		reserve(student2, a02, "11:00", "13:00");
		ReservationResult inUse = reservationService.reserve(student2.getId(), a03.getId(), TODAY,
				LocalTime.parse("09:00"), LocalTime.parse("10:30"));
		Reservation checkedIn = reservationRepository.findById(inUse.reservationId()).orElseThrow();
		checkedIn.checkIn(NOW);
		reservationRepository.save(checkedIn);

		SeatAvailability result = seatService.getAvailability(student1.getId(), TODAY,
				LocalTime.parse("10:00"), LocalTime.parse("12:00"));

		assertThat(result.bookable()).isTrue();
		assertThat(stateOf(result, a01)).isEqualTo(SeatState.MINE);
		assertThat(stateOf(result, a02)).isEqualTo(SeatState.RESERVED);
		assertThat(stateOf(result, a03)).isEqualTo(SeatState.IN_USE);
		assertThat(stateOf(result, a04)).isEqualTo(SeatState.AVAILABLE);
		assertThat(stateOf(result, closed)).isEqualTo(SeatState.UNAVAILABLE);
		assertThat(result.seats().stream().filter(s -> s.state() == SeatState.MINE).findFirst().orElseThrow()
				.myReservationId()).isEqualTo(mine.reservationId());
	}

	@Test
	void US03_겹치지_않는_시간대로_조회하면_예약가능으로_보인다() {
		reserve(student2, a01, "10:00", "12:00");

		SeatAvailability result = seatService.getAvailability(student1.getId(), TODAY,
				LocalTime.parse("12:00"), LocalTime.parse("14:00"));

		assertThat(stateOf(result, a01)).isEqualTo(SeatState.AVAILABLE);
	}

	@Test
	void US03_취소한_예약의_좌석은_예약가능으로_보인다() {
		ReservationResult r = reserve(student2, a01, "10:00", "12:00");
		reservationService.cancel(student2.getId(), r.reservationId(), CancelReason.TIME_CHANGE);

		SeatAvailability result = seatService.getAvailability(student1.getId(), TODAY,
				LocalTime.parse("10:00"), LocalTime.parse("12:00"));

		assertThat(stateOf(result, a01)).isEqualTo(SeatState.AVAILABLE);
	}

	@Test
	void US03_정책_범위를_벗어난_시간대는_예약_불가로_안내되고_정책이_함께_표시된다() {
		SeatAvailability result = seatService.getAvailability(student1.getId(), TODAY,
				LocalTime.parse("10:00"), LocalTime.parse("15:00"));

		assertThat(result.bookable()).isFalse();
		assertThat(result.notice()).contains("최대 4시간");
		assertThat(result.policy().maxHours()).isEqualTo(4);
		assertThat(result.policy().lastBookableDate()).isEqualTo(TODAY.plusDays(7));
	}

	@Test
	void US03_시험기간이면_시험기간_정책이_표시된다() {
		policyRepository.save(ReservationPolicy.exam(TODAY, TODAY.plusDays(5), 1, 3));

		SeatAvailability result = seatService.getAvailability(student1.getId(), TODAY,
				LocalTime.parse("10:00"), LocalTime.parse("12:00"));

		assertThat(result.policy().maxHours()).isEqualTo(3);
		assertThat(result.policy().lastBookableDate()).isEqualTo(TODAY.plusDays(1));
	}
}
