package com.cnu.lsrs.service;

import com.cnu.lsrs.config.LsrsProperties;
import com.cnu.lsrs.domain.PeriodType;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminServiceTest {

	@Autowired
	AdminService adminService;

	@Autowired
	PolicyService policyService;

	@Autowired
	UserRepository userRepository;

	@Autowired
	SeatRepository seatRepository;

	@Autowired
	ReservationRepository reservationRepository;

	@Autowired
	ReservationPolicyRepository policyRepository;

	@Autowired
	NoShowRecordRepository noShowRecordRepository;

	@Autowired
	LsrsProperties properties;

	@Autowired
	Clock clock;

	private User student;
	private Seat seat;

	@BeforeEach
	void setUp() {
		noShowRecordRepository.deleteAll();
		reservationRepository.deleteAll();
		policyRepository.deleteAll();
		seatRepository.deleteAll();
		userRepository.deleteAll();

		student = userRepository.save(User.createActive("20230001", "student@lsrs.test", "hash", Role.STUDENT));
		seat = seatRepository.save(Seat.create("A-01", "1층 열람실 A"));
		policyRepository.save(ReservationPolicy.normal(7, 4));
	}

	@Test
	void 대시보드는_현재_이용_대기_잔여_좌석을_계산한다() {
		LocalDateTime now = LocalDateTime.now(clock);
		Reservation checkedIn = Reservation.create(student, seat, now.minusMinutes(1), now.plusHours(1),
				"checked-in-token", now.minusHours(1));
		checkedIn.checkIn(now);
		reservationRepository.save(checkedIn);
		Seat pendingSeat = seatRepository.save(Seat.create("A-02", "1층 열람실 A"));
		reservationRepository.save(Reservation.create(student, pendingSeat, now.minusMinutes(2),
				now.plusHours(1), "pending-token", now.minusHours(1)));
		Seat unavailableSeat = seatRepository.save(Seat.create("A-03", "1층 열람실 A"));
		unavailableSeat.markUnavailable();

		AdminService.DashboardResponse dashboard = adminService.getDashboard();

		assertThat(dashboard.currentUsers()).isEqualTo(1);
		assertThat(dashboard.checkInPendingSeats()).isEqualTo(1);
		assertThat(dashboard.remainingSeats()).isZero();
		assertThat(dashboard.unavailableSeats()).isEqualTo(1);
		assertThat(dashboard.utilizationRate()).isEqualByComparingTo("50.0");
	}

	@Test
	void 통계는_시간대별_좌석_이용_분과_가동률을_계산한다() {
		LocalDateTime hour = LocalDateTime.now(clock).truncatedTo(java.time.temporal.ChronoUnit.HOURS);
		Reservation reservation = Reservation.create(student, seat, hour.plusMinutes(30),
				hour.plusMinutes(90), "statistics-token", hour);
		reservation.checkIn(hour.plusMinutes(30));
		reservationRepository.save(reservation);

		AdminService.StatisticsResponse statistics = adminService.getStatistics(hour, hour.plusHours(2));

		assertThat(statistics.points()).hasSize(2)
				.extracting(AdminService.StatisticsPoint::userCount)
				.containsExactly(1, 1);
		assertThat(statistics.points()).extracting(AdminService.StatisticsPoint::utilizationRate)
				.containsExactly(new java.math.BigDecimal("50.0"), new java.math.BigDecimal("50.0"));
		assertThat(statistics.averageUtilizationRate()).isEqualByComparingTo("50.0");
	}

	@Test
	void 관리자_패널티가_기준_횟수에_도달하면_제한과_이력을_만든다() {
		LocalDateTime now = LocalDateTime.now(clock);
		Reservation reservation = reservationRepository.save(Reservation.create(student, seat,
				now.minusMinutes(5), now.plusHours(1), "force-cancel-token", now.minusHours(1)));
		for (int i = 0; i < properties.policy().noShowThreshold() - 1; i++) {
			student.recordNoShow(properties.policy().noShowThreshold(), properties.policy().restrictionPeriod(), now);
		}

		AdminService.CancellationResponse result = adminService.forceCancel(reservation.getId(),
				com.cnu.lsrs.domain.CancelReason.RULE_VIOLATION, true);

		assertThat(result.status()).isEqualTo(ReservationStatus.CANCELLED);
		assertThat(noShowRecordRepository.count()).isEqualTo(1);
		assertThat(student.getNoShowCount()).isZero();
		assertThat(student.getRestrictionUntil())
				.isEqualTo(result.cancelledAt().plus(properties.policy().restrictionPeriod()));
	}

	@Test
	void 시험기간을_저장하면_적용일의_정책이_갱신된다() {
		LocalDate start = LocalDate.of(2026, 12, 7);
		LocalDate end = LocalDate.of(2026, 12, 18);

		AdminService.PolicyValue saved = adminService.saveExamPolicy(start, end);

		ReservationPolicy active = policyService.getActivePolicy(start);
		assertThat(saved.periodType()).isEqualTo(PeriodType.EXAM);
		assertThat(active.getStartDate()).isEqualTo(start);
		assertThat(active.getEndDate()).isEqualTo(end);
		assertThat(active.getBookingDays()).isEqualTo(properties.policy().exam().bookingDays());
		assertThat(active.getMaxHours()).isEqualTo(properties.policy().exam().maxHours());
	}

	@Test
	void 시험기간_정책을_해제하면_평상시_정책으로_돌아간다() {
		adminService.saveExamPolicy(LocalDate.of(2026, 12, 7), LocalDate.of(2026, 12, 18));

		adminService.clearExamPolicies();

		assertThat(policyRepository.findAllByPeriodTypeOrderByStartDateDesc(PeriodType.EXAM)).isEmpty();
		assertThat(policyService.getActivePolicy(LocalDate.of(2026, 12, 10)).getPeriodType())
				.isEqualTo(PeriodType.NORMAL);
	}

	@Test
	void 제한을_해제하면_노쇼_카운터도_초기화된다() {
		LocalDateTime now = LocalDateTime.now(clock);
		student.recordNoShow(properties.policy().noShowThreshold(), properties.policy().restrictionPeriod(), now);
		student.setRestrictionUntil(now.plusDays(2));

		AdminService.RestrictionResponse response = adminService.updateRestriction(student.getId(), false, null);

		assertThat(response.restricted()).isFalse();
		assertThat(response.restrictionUntil()).isNull();
		assertThat(response.noShowCount()).isZero();
	}

	@Test
	void 노쇼_횟수만_초기화하고_제한과_현재_예약만_사용자_목록에_포함한다() {
		LocalDateTime now = LocalDateTime.now(clock);
		student.recordNoShow(properties.policy().noShowThreshold(), properties.policy().restrictionPeriod(), now);
		student.setRestrictionUntil(now.plusDays(2));
		reservationRepository.save(Reservation.create(student, seat, now.minusMinutes(1), now.plusHours(1),
				"current-reservation-token", now.minusHours(1)));
		Seat futureSeat = seatRepository.save(Seat.create("A-02", "1층 열람실 A"));
		reservationRepository.save(Reservation.create(student, futureSeat, now.plusHours(1), now.plusHours(2),
				"future-reservation-token", now));
		Seat pastSeat = seatRepository.save(Seat.create("A-03", "1층 열람실 A"));
		Reservation past = Reservation.create(student, pastSeat, now.minusHours(3), now.minusHours(2),
				"past-reservation-token", now.minusHours(4));
		past.checkIn(now.minusHours(3));
		reservationRepository.save(past);

		AdminService.UserHistory userBeforeReset = adminService.findUsers(student.getStudentNo()).get(0);
		AdminService.RestrictionResponse reset = adminService.resetNoShowCount(student.getId());
		AdminService.UserHistory userAfterReset = adminService.findUsers(student.getStudentNo()).get(0);

		assertThat(userBeforeReset.currentReservations()).extracting(AdminService.ReservationHistory::seatCode)
				.containsExactly("A-02", "A-01");
		assertThat(reset.noShowCount()).isZero();
		assertThat(reset.restricted()).isTrue();
		assertThat(userAfterReset.currentReservations()).hasSize(2);
	}

	@Test
	void 관리자가_사용자_역할을_변경할_수_있다() {
		AdminService.UserRoleResponse response = adminService.updateUserRole(student.getId(), Role.ADMIN);

		assertThat(response.role()).isEqualTo(Role.ADMIN);
		assertThat(userRepository.findById(student.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
	}
}
