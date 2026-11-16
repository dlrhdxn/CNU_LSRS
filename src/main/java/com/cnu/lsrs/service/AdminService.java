package com.cnu.lsrs.service;

import com.cnu.lsrs.common.exception.BusinessException;
import com.cnu.lsrs.config.LsrsProperties;
import com.cnu.lsrs.domain.AccountStatus;
import com.cnu.lsrs.domain.CancelReason;
import com.cnu.lsrs.domain.NoShowRecord;
import com.cnu.lsrs.domain.NoShowSource;
import com.cnu.lsrs.domain.PeriodType;
import com.cnu.lsrs.domain.Reservation;
import com.cnu.lsrs.domain.ReservationPolicy;
import com.cnu.lsrs.domain.ReservationStatus;
import com.cnu.lsrs.domain.Role;
import com.cnu.lsrs.domain.Seat;
import com.cnu.lsrs.domain.SeatStatus;
import com.cnu.lsrs.domain.User;
import com.cnu.lsrs.repository.NoShowRecordRepository;
import com.cnu.lsrs.repository.ReservationPolicyRepository;
import com.cnu.lsrs.repository.ReservationRepository;
import com.cnu.lsrs.repository.SeatRepository;
import com.cnu.lsrs.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AdminService {

	private static final List<ReservationStatus> ACTIVE_STATUSES =
			List.of(ReservationStatus.RESERVED, ReservationStatus.CHECKED_IN);

	private final SeatRepository seatRepository;
	private final ReservationRepository reservationRepository;
	private final UserRepository userRepository;
	private final NoShowRecordRepository noShowRecordRepository;
	private final ReservationPolicyRepository policyRepository;
	private final LsrsProperties properties;
	private final Clock clock;

	public AdminService(SeatRepository seatRepository, ReservationRepository reservationRepository,
						UserRepository userRepository, NoShowRecordRepository noShowRecordRepository,
						ReservationPolicyRepository policyRepository, LsrsProperties properties, Clock clock) {
		this.seatRepository = seatRepository;
		this.reservationRepository = reservationRepository;
		this.userRepository = userRepository;
		this.noShowRecordRepository = noShowRecordRepository;
		this.policyRepository = policyRepository;
		this.properties = properties;
		this.clock = clock;
	}

	public DashboardResponse getDashboard() {
		LocalDateTime now = LocalDateTime.now(clock);
		List<Seat> seats = seatRepository.findAll();
		List<Reservation> reservations = reservationRepository
				.findAllByStatusInAndStartAtLessThanEqualAndEndAtGreaterThan(ACTIVE_STATUSES, now, now);
		Map<Long, Reservation> currentBySeat = reservations.stream()
				.collect(Collectors.toMap(r -> r.getSeat().getId(), Function.identity()));

		List<SeatSummary> summaries = seats.stream()
				.sorted(Comparator.comparing(Seat::getSeatCode))
				.map(seat -> toSeatSummary(seat, currentBySeat.get(seat.getId())))
				.toList();
		long inUse = summaries.stream().filter(seat -> "IN_USE".equals(seat.status())).count();
		long checkInPending = summaries.stream().filter(seat -> "CHECK_IN_PENDING".equals(seat.status())).count();
		long available = summaries.stream().filter(seat -> "AVAILABLE".equals(seat.status())).count();
		long operable = seats.stream().filter(Seat::isAvailable).count();
		BigDecimal utilization = operable == 0 ? BigDecimal.ZERO
				: BigDecimal.valueOf(inUse * 100.0 / operable).setScale(1, RoundingMode.HALF_UP);

		return new DashboardResponse((int) inUse, (int) available, (int) checkInPending,
				seats.size(), (int) (seats.size() - operable), utilization, summaries);
	}

	public List<UserHistory> findUsers(String studentNo) {
		List<User> users = userRepository.findAll().stream()
				.filter(user -> studentNo == null || studentNo.isBlank()
						|| user.getStudentNo().equalsIgnoreCase(studentNo.trim()))
				.sorted(Comparator.comparing(User::getStudentNo))
				.toList();
		return users.stream().map(this::toUserHistory).toList();
	}

	public StatisticsResponse getStatistics(LocalDateTime startAt, LocalDateTime endAt) {
		if (startAt == null || endAt == null || !startAt.isBefore(endAt)) {
			throw BusinessException.badRequest("INVALID_STATISTICS_RANGE", "통계 시작 시각은 종료 시각보다 앞서야 합니다.");
		}

		List<Seat> operableSeats = seatRepository.findAll().stream().filter(Seat::isAvailable).toList();
		List<Reservation> reservations = reservationRepository
				.findAllByStartAtLessThanAndEndAtGreaterThanAndCheckedInAtIsNotNull(endAt, startAt);
		List<StatisticsPoint> points = new ArrayList<>();
		long totalOccupiedSeatMinutes = 0;
		long totalCapacitySeatMinutes = 0;
		LocalDateTime bucketStart = startAt.truncatedTo(ChronoUnit.HOURS);
		while (bucketStart.isBefore(endAt)) {
			LocalDateTime currentBucketStart = bucketStart;
			LocalDateTime intervalStart = currentBucketStart.isBefore(startAt) ? startAt : currentBucketStart;
			LocalDateTime nextBucket = currentBucketStart.plusHours(1);
			LocalDateTime intervalEnd = nextBucket.isAfter(endAt) ? endAt : nextBucket;
			long bucketMinutes = Duration.between(intervalStart, intervalEnd).toMinutes();
			List<Reservation> bucketReservations = reservations.stream()
					.filter(reservation -> reservation.getCheckedInAt().isBefore(intervalEnd)
							&& intervalStart.isBefore(reservation.getEndAt()))
					.toList();

			long occupiedSeatMinutes = bucketReservations.stream()
					.mapToLong(reservation -> overlapMinutes(
							reservation, intervalStart, intervalEnd,
							reservation.getCheckedInAt().isAfter(reservation.getStartAt())
									? reservation.getCheckedInAt() : reservation.getStartAt()))
					.sum();
			long capacitySeatMinutes = (long) operableSeats.size() * bucketMinutes;
			long userCount = bucketReservations.stream()
					.map(reservation -> reservation.getUser().getId())
					.distinct()
					.count();
			totalOccupiedSeatMinutes += occupiedSeatMinutes;
			totalCapacitySeatMinutes += capacitySeatMinutes;
			points.add(new StatisticsPoint(currentBucketStart, (int) userCount,
					percentage(occupiedSeatMinutes, capacitySeatMinutes)));
			bucketStart = nextBucket;
		}
		return new StatisticsResponse(startAt, endAt, points,
				percentage(totalOccupiedSeatMinutes, totalCapacitySeatMinutes));
	}

	@Transactional
	public RestrictionResponse updateRestriction(Long userId, boolean restricted, LocalDateTime restrictionUntil) {
		User user = findStudent(userId);
		LocalDateTime now = LocalDateTime.now(clock);
		if (restricted) {
			if (restrictionUntil == null || !restrictionUntil.isAfter(now)) {
				throw BusinessException.badRequest("INVALID_RESTRICTION_PERIOD", "제한 해제 시각은 현재 시각보다 뒤여야 합니다.");
			}
			user.setRestrictionUntil(restrictionUntil);
		} else {
			user.clearRestriction();
		}
		return new RestrictionResponse(user.getId(), user.isRestricted(now), user.getRestrictionUntil(),
				user.getNoShowCount());
	}

	@Transactional
	public CancellationResponse forceCancel(Long reservationId, CancelReason reason, boolean applyNoShowPenalty) {
		if (reason == null || !reason.isAdminReason()) {
			throw BusinessException.badRequest("INVALID_ADMIN_CANCEL_REASON", "관리자 강제 취소 사유를 선택해야 합니다.");
		}
		Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
				.orElseThrow(() -> BusinessException.notFound("RESERVATION_NOT_FOUND", "예약을 찾을 수 없습니다."));
		if (!reservation.getStatus().isActive()) {
			throw BusinessException.conflict("RESERVATION_NOT_ACTIVE", "활성 예약만 강제 취소할 수 있습니다.");
		}
		reservation.cancelByAdmin(reason);
		LocalDateTime now = LocalDateTime.now(clock);
		if (applyNoShowPenalty) {
			User user = reservation.getUser();
			user.recordNoShow(properties.policy().noShowThreshold(), properties.policy().restrictionPeriod(), now);
			noShowRecordRepository.save(NoShowRecord.of(reservation, NoShowSource.ADMIN, now));
		}
		return new CancellationResponse(reservation.getId(), reservation.getStatus(), reason,
				applyNoShowPenalty, now);
	}

	public PolicySettings getPolicies() {
		ReservationPolicy normal = policyRepository.findFirstByPeriodTypeOrderByIdAsc(PeriodType.NORMAL)
				.orElse(null);
		List<ReservationPolicy> exams = policyRepository.findAllByPeriodTypeOrderByStartDateDesc(PeriodType.EXAM);
		LsrsProperties.PeriodDefaults defaults = properties.policy().normal();
		PolicyValue normalValue = normal == null
				? new PolicyValue(null, PeriodType.NORMAL, null, null, defaults.bookingDays(), defaults.maxHours())
				: PolicyValue.from(normal);
		return new PolicySettings(normalValue, exams.stream().map(PolicyValue::from).toList(),
				properties.policy().exam().bookingDays(), properties.policy().exam().maxHours());
	}

	@Transactional
	public PolicyValue saveExamPolicy(LocalDate startDate, LocalDate endDate) {
		if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
			throw BusinessException.badRequest("INVALID_EXAM_PERIOD", "시험기간 시작일과 종료일을 확인해 주세요.");
		}
		LsrsProperties.PeriodDefaults defaults = properties.policy().exam();
		ReservationPolicy policy = policyRepository.findFirstByPeriodTypeOrderByIdDesc(PeriodType.EXAM)
				.orElseGet(() -> ReservationPolicy.exam(startDate, endDate,
						defaults.bookingDays(), defaults.maxHours()));
		if (policy.getId() != null) {
			policy.updateExamPeriod(startDate, endDate, defaults.bookingDays(), defaults.maxHours());
		}
		ReservationPolicy saved = policyRepository.save(policy);
		return PolicyValue.from(saved);
	}

	@Transactional
	public void clearExamPolicies() {
		List<ReservationPolicy> examPolicies =
				policyRepository.findAllByPeriodTypeOrderByStartDateDesc(PeriodType.EXAM);
		policyRepository.deleteAll(examPolicies);
	}

	private SeatSummary toSeatSummary(Seat seat, Reservation reservation) {
		if (seat.getStatus() == SeatStatus.UNAVAILABLE) {
			return new SeatSummary(seat.getSeatCode(), seat.getLocation(), "UNAVAILABLE", null, null,
					null, null);
		}
		if (reservation == null) {
			return new SeatSummary(seat.getSeatCode(), seat.getLocation(), "AVAILABLE", null, null, null, null);
		}
		String status = reservation.getStatus() == ReservationStatus.CHECKED_IN
				? "IN_USE" : "CHECK_IN_PENDING";
		return new SeatSummary(seat.getSeatCode(), seat.getLocation(), status, reservation.getId(),
				reservation.getUser().getStudentNo(), reservation.getStartAt(), reservation.getEndAt());
	}

	private UserHistory toUserHistory(User user) {
		LocalDateTime now = LocalDateTime.now(clock);
		List<ReservationHistory> currentReservations = user.getRole() == Role.STUDENT
				? reservationRepository.findAllByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
						.filter(reservation -> reservation.getStatus().isActive())
						.filter(reservation -> reservation.getEndAt().isAfter(now))
						.map(this::toReservationHistory).toList()
				: List.of();
		return new UserHistory(user.getId(), user.getStudentNo(), user.getEmail(), user.getRole(),
				user.getAccountStatus(), user.getNoShowCount(), user.getRestrictionUntil(),
				user.isRestricted(now), currentReservations);
	}

	@Transactional
	public RestrictionResponse resetNoShowCount(Long userId) {
		User user = findStudent(userId);
		user.resetNoShowCount();
		LocalDateTime now = LocalDateTime.now(clock);
		return new RestrictionResponse(user.getId(), user.isRestricted(now), user.getRestrictionUntil(),
				user.getNoShowCount());
	}

	@Transactional
	public UserRoleResponse updateUserRole(Long userId, Role role) {
		if (role == null) {
			throw BusinessException.badRequest("INVALID_USER_ROLE", "사용자 역할을 선택해야 합니다.");
		}
		User user = userRepository.findById(userId)
				.orElseThrow(() -> BusinessException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
		user.changeRole(role);
		return new UserRoleResponse(user.getId(), user.getStudentNo(), user.getRole());
	}

	private static long overlapMinutes(Reservation reservation, LocalDateTime startAt, LocalDateTime endAt) {
		return overlapMinutes(reservation, startAt, endAt, reservation.getStartAt());
	}

	private static long overlapMinutes(Reservation reservation, LocalDateTime startAt, LocalDateTime endAt,
									   LocalDateTime usageStart) {
		LocalDateTime overlapStart = usageStart.isAfter(startAt) ? usageStart : startAt;
		LocalDateTime overlapEnd = reservation.getEndAt().isBefore(endAt)
				? reservation.getEndAt() : endAt;
		return Math.max(0, Duration.between(overlapStart, overlapEnd).toMinutes());
	}

	private static BigDecimal percentage(long value, long total) {
		return total == 0 ? BigDecimal.ZERO
				: BigDecimal.valueOf(value * 100.0 / total).setScale(1, RoundingMode.HALF_UP);
	}

	private ReservationHistory toReservationHistory(Reservation reservation) {
		return new ReservationHistory(reservation.getId(), reservation.getUser().getId(),
				reservation.getUser().getStudentNo(), reservation.getSeat().getSeatCode(),
				reservation.getStartAt(), reservation.getEndAt(), reservation.getStatus(),
				reservation.getCheckedInAt(), reservation.getCancelReason(), reservation.getCreatedAt());
	}

	private User findStudent(Long userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> BusinessException.notFound("STUDENT_NOT_FOUND", "학생 계정을 찾을 수 없습니다."));
		if (user.getRole() != Role.STUDENT) {
			throw BusinessException.notFound("STUDENT_NOT_FOUND", "학생 계정을 찾을 수 없습니다.");
		}
		return user;
	}

	public record DashboardResponse(int currentUsers, int remainingSeats, int checkInPendingSeats,
								   int totalSeats, int unavailableSeats, BigDecimal utilizationRate,
								   List<SeatSummary> seats) {
	}

	public record SeatSummary(String seatCode, String location, String status, Long reservationId,
							  String studentNo, LocalDateTime startAt, LocalDateTime endAt) {
	}

	public record UserHistory(Long userId, String studentNo, String email, Role role,
							  AccountStatus accountStatus, int noShowCount,
							  LocalDateTime restrictionUntil, boolean restricted,
							  List<ReservationHistory> currentReservations) {
	}

	public record StatisticsResponse(LocalDateTime startAt, LocalDateTime endAt,
									 List<StatisticsPoint> points, BigDecimal averageUtilizationRate) {
	}

	public record StatisticsPoint(LocalDateTime bucketStart, int userCount, BigDecimal utilizationRate) {
	}

	public record ReservationHistory(Long reservationId, Long userId, String studentNo, String seatCode,
									 LocalDateTime startAt, LocalDateTime endAt, ReservationStatus status,
									 LocalDateTime checkedInAt, CancelReason cancelReason,
									 LocalDateTime createdAt) {
	}

	public record RestrictionResponse(Long userId, boolean restricted, LocalDateTime restrictionUntil,
									  int noShowCount) {
	}

	public record UserRoleResponse(Long userId, String studentNo, Role role) {
	}

	public record CancellationResponse(Long reservationId, ReservationStatus status, CancelReason reason,
									   boolean noShowPenaltyApplied, LocalDateTime cancelledAt) {
	}

	public record PolicySettings(PolicyValue normal, List<PolicyValue> exams,
								 int examBookingDays, int examMaxHours) {
	}

	public record PolicyValue(Long policyId, PeriodType periodType, LocalDate startDate, LocalDate endDate,
							  int bookingDays, int maxHours) {
		private static PolicyValue from(ReservationPolicy policy) {
			return new PolicyValue(policy.getId(), policy.getPeriodType(), policy.getStartDate(),
					policy.getEndDate(), policy.getBookingDays(), policy.getMaxHours());
		}
	}
}
