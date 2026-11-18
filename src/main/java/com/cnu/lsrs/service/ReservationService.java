package com.cnu.lsrs.service;

import com.cnu.lsrs.common.exception.BusinessException;
import com.cnu.lsrs.config.LsrsProperties;
import com.cnu.lsrs.domain.CancelReason;
import com.cnu.lsrs.domain.Reservation;
import com.cnu.lsrs.domain.ReservationPolicy;
import com.cnu.lsrs.domain.ReservationStatus;
import com.cnu.lsrs.domain.Seat;
import com.cnu.lsrs.domain.User;
import com.cnu.lsrs.repository.ReservationRepository;
import com.cnu.lsrs.repository.SeatRepository;
import com.cnu.lsrs.repository.UserRepository;
import com.cnu.lsrs.service.dto.ReservationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 좌석 예약(US-04, Core Flow 1)과 학생 직접 취소(US-05). */
@Service
@Transactional
public class ReservationService {

	private static final List<ReservationStatus> ACTIVE =
			List.of(ReservationStatus.RESERVED, ReservationStatus.CHECKED_IN);
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final ReservationRepository reservationRepository;
	private final SeatRepository seatRepository;
	private final UserRepository userRepository;
	private final PolicyService policyService;
	private final BookingRules bookingRules;
	private final QrTokenGenerator qrTokenGenerator;
	private final LsrsProperties properties;
	private final Clock clock;

	public ReservationService(ReservationRepository reservationRepository, SeatRepository seatRepository,
							  UserRepository userRepository, PolicyService policyService, BookingRules bookingRules,
							  QrTokenGenerator qrTokenGenerator, LsrsProperties properties, Clock clock) {
		this.reservationRepository = reservationRepository;
		this.seatRepository = seatRepository;
		this.userRepository = userRepository;
		this.policyService = policyService;
		this.bookingRules = bookingRules;
		this.qrTokenGenerator = qrTokenGenerator;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * 예약 생성 (US-04, FR-R-10~13).
	 *
	 * 동시성: 학생 행 → 좌석 행 순서로 SELECT … FOR UPDATE를 건 뒤에 겹침을 검사한다.
	 *  - 같은 좌석 동시 요청은 좌석 잠금에서 한 줄로 서므로 하나만 확정된다.
	 *  - 같은 학생이 다른 좌석으로 동시에 요청해도 학생 잠금에서 한 줄로 선다 (FR-R-11).
	 *  - 잠그는 순서를 항상 같게 해서 교착 상태를 막는다.
	 *  - 잠금보다 먼저 일반 SELECT를 하지 않는다. MySQL(REPEATABLE READ)은 첫 일반 SELECT 시점의
	 *    스냅숏을 쓰므로, 잠금을 기다리는 동안 앞 요청이 커밋한 예약을 못 볼 수 있다.
	 */
	public ReservationResult reserve(Long userId, Long seatId, LocalDate date, LocalTime startAt, LocalTime endAt) {
		User user = userRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> BusinessException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
		Seat seat = seatRepository.findByIdForUpdate(seatId)
				.orElseThrow(() -> BusinessException.notFound("SEAT_NOT_FOUND", "존재하지 않는 좌석입니다."));

		LocalDateTime now = LocalDateTime.now(clock);

		// FR-R-12: 이용 제한 중이면 해제일 안내
		if (user.isRestricted(now)) {
			throw BusinessException.forbidden("RESERVATION_RESTRICTED",
					"반복 노쇼로 이용이 제한된 상태입니다. " + user.getRestrictionUntil().format(DATE_TIME)
							+ " 이후 다시 예약할 수 있습니다.");
		}

		ReservationPolicy policy = policyService.getActivePolicy(date);
		bookingRules.check(date, startAt, endAt, now, policy).ifPresent(v -> {
			throw v.toException();
		});

		if (!seat.isAvailable()) {
			throw BusinessException.conflict("SEAT_UNAVAILABLE", "관리자가 사용 불가로 지정한 좌석입니다.");
		}

		LocalDateTime from = LocalDateTime.of(date, startAt);
		LocalDateTime to = LocalDateTime.of(date, endAt);
		if (reservationRepository.existsSeatOverlap(seatId, from, to, ACTIVE)) {
			throw BusinessException.conflict("SEAT_ALREADY_RESERVED", "이미 예약된 좌석입니다. 다른 좌석이나 시간대를 선택해 주세요.");
		}
		if (reservationRepository.existsUserOverlap(userId, from, to, ACTIVE)) {
			throw BusinessException.conflict("USER_TIME_OVERLAP", "같은 시간대에 이미 다른 예약이 있습니다.");
		}

		Reservation reservation = Reservation.create(user, seat, from, to, qrTokenGenerator.generate(), now);
		return ReservationResult.from(reservationRepository.save(reservation));
	}

	/**
	 * 학생 직접 취소 (US-05, FR-X-01~04).
	 * 체크인 전(RESERVED) 본인 예약만 취소할 수 있고 노쇼로 기록하지 않는다.
	 * 체크인 마감(시작 + checkInWindow, FR-C-02)이 지나면 취소할 수 없다. 마감 뒤 취소를 허용하면
	 * 노쇼 스케줄러가 돌기 전에 취소해서 노쇼 기록(FR-X-10)을 피할 수 있기 때문이다.
	 * 경계는 CheckInService와 같다: 마감 시각까지는 체크인·취소 모두 가능하다.
	 * 상태가 CANCELLED가 되면 겹침 검사 대상(ACTIVE)에서 빠지므로 좌석은 즉시 다시 예약할 수 있다.
	 */
	public ReservationResult cancel(Long userId, Long reservationId, CancelReason reason) {
		if (reason.isAdminReason()) {
			throw BusinessException.badRequest("INVALID_CANCEL_REASON", "학생이 선택할 수 없는 취소 사유입니다.");
		}
		Reservation reservation = reservationRepository.findByIdWithSeatForUpdate(reservationId)
				.orElseThrow(() -> BusinessException.notFound("RESERVATION_NOT_FOUND", "예약을 찾을 수 없습니다."));
		if (!reservation.isOwnedBy(userId)) {
			throw BusinessException.forbidden("RESERVATION_FORBIDDEN", "본인의 예약만 취소할 수 있습니다.");
		}
		if (reservation.getStatus() != ReservationStatus.RESERVED) {
			throw BusinessException.conflict("RESERVATION_NOT_CANCELLABLE",
					"체크인 전(예약중) 상태의 예약만 취소할 수 있습니다.");
		}
		LocalDateTime deadline = reservation.getStartAt().plus(properties.policy().checkInWindow());
		if (LocalDateTime.now(clock).isAfter(deadline)) {
			throw BusinessException.conflict("CANCEL_DEADLINE_PASSED",
					"체크인 마감 시각(" + deadline.format(DATE_TIME) + ")이 지나 취소할 수 없습니다.");
		}
		reservation.cancelByStudent(reason);
		return ReservationResult.from(reservation);
	}

	@Transactional(readOnly = true)
	public List<MyReservation> getMyReservations(Long userId) {
		LocalDateTime now = LocalDateTime.now(clock);
		return reservationRepository.findAllByUser_IdOrderByCreatedAtDesc(userId).stream()
				.map(reservation -> {
					LocalDateTime checkInDeadline =
							reservation.getStartAt().plus(properties.policy().checkInWindow());
					boolean qrAvailable = reservation.getStatus() == ReservationStatus.RESERVED
							&& !now.isBefore(reservation.getStartAt())
							&& !now.isAfter(checkInDeadline);
					return new MyReservation(
							reservation.getId(),
							reservation.getSeat().getSeatCode(),
							reservation.getStartAt().format(DATE_TIME),
							reservation.getEndAt().format(DATE_TIME),
							reservation.getStatus().name(),
							reservation.getCancelReason() == null ? null : reservation.getCancelReason().getLabel(),
							reservation.getStartAt().atZone(SEOUL).toInstant().toEpochMilli(),
							reservation.getEndAt().atZone(SEOUL).toInstant().toEpochMilli(),
							reservation.getStatus().isActive()
									? Math.max(0, Duration.between(now, reservation.getEndAt()).toSeconds())
									: null,
							qrAvailable,
							qrAvailable ? Math.max(0, (Duration.between(now, checkInDeadline).toMillis() + 999) / 1000)
									: null);
				})
				.toList();
	}

	public record MyReservation(Long reservationId, String seatCode, String startAt, String endAt,
								String status, String cancelReason, long startAtEpochMillis,
								long endAtEpochMillis, Long remainingSeconds, boolean qrAvailable,
								Long qrRemainingSeconds) {
	}
}
