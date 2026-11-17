package com.cnu.lsrs.service;

import com.cnu.lsrs.domain.Reservation;
import com.cnu.lsrs.domain.ReservationPolicy;
import com.cnu.lsrs.domain.ReservationStatus;
import com.cnu.lsrs.domain.Seat;
import com.cnu.lsrs.repository.ReservationRepository;
import com.cnu.lsrs.repository.SeatRepository;
import com.cnu.lsrs.service.dto.PolicyInfo;
import com.cnu.lsrs.service.dto.SeatAvailability;
import com.cnu.lsrs.service.dto.SeatView;
import com.cnu.lsrs.service.dto.SeatView.SeatState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 좌석 현황 조회 (US-03, FR-R-01~03).
 * 좌석 목록 1번 + 겹치는 활성 예약 1번, 쿼리 2번으로 끝낸다 (NFR 4.4: 500ms 이내).
 */
@Service
@Transactional(readOnly = true)
public class SeatService {

	private static final List<ReservationStatus> ACTIVE =
			List.of(ReservationStatus.RESERVED, ReservationStatus.CHECKED_IN);

	private final SeatRepository seatRepository;
	private final ReservationRepository reservationRepository;
	private final PolicyService policyService;
	private final BookingRules bookingRules;
	private final Clock clock;

	public SeatService(SeatRepository seatRepository, ReservationRepository reservationRepository,
					   PolicyService policyService, BookingRules bookingRules, Clock clock) {
		this.seatRepository = seatRepository;
		this.reservationRepository = reservationRepository;
		this.policyService = policyService;
		this.bookingRules = bookingRules;
		this.clock = clock;
	}

	public SeatAvailability getAvailability(Long userId, LocalDate date, LocalTime startAt, LocalTime endAt) {
		LocalDateTime now = LocalDateTime.now(clock);
		ReservationPolicy policy = policyService.getActivePolicy(date);
		Optional<BookingRules.Violation> violation = bookingRules.check(date, startAt, endAt, now, policy);
		PolicyInfo policyInfo = PolicyInfo.of(policy, now.toLocalDate());

		// 시작>=종료면 겹침 계산 자체가 의미 없으므로 좌석만 보여준다
		List<Reservation> overlapping = startAt.isBefore(endAt)
				? reservationRepository.findOverlapping(LocalDateTime.of(date, startAt), LocalDateTime.of(date, endAt), ACTIVE)
				: List.of();

		Map<Long, List<Reservation>> bySeat = new HashMap<>();
		for (Reservation r : overlapping) {
			bySeat.computeIfAbsent(r.getSeat().getId(), k -> new ArrayList<>()).add(r);
		}

		List<SeatView> seats = seatRepository.findAllByOrderBySeatCodeAsc().stream()
				.map(seat -> toView(seat, bySeat.getOrDefault(seat.getId(), List.of()), userId))
				.toList();

		return new SeatAvailability(date, startAt, endAt, policyInfo, violation.isEmpty(),
				violation.map(BookingRules.Violation::message).orElse(null), seats);
	}

	/** 상태 우선순위: 내 예약 > 사용 중 > 예약됨 > 사용 불가 > 예약 가능 */
	private static SeatView toView(Seat seat, List<Reservation> reservations, Long userId) {
		for (Reservation r : reservations) {
			if (r.isOwnedBy(userId)) {
				return new SeatView(seat.getId(), seat.getSeatCode(), seat.getLocation(), SeatState.MINE,
						r.getId(), r.getStatus());
			}
		}
		SeatState state;
		if (reservations.stream().anyMatch(r -> r.getStatus() == ReservationStatus.CHECKED_IN)) {
			state = SeatState.IN_USE;
		} else if (!reservations.isEmpty()) {
			state = SeatState.RESERVED;
		} else if (!seat.isAvailable()) {
			state = SeatState.UNAVAILABLE;
		} else {
			state = SeatState.AVAILABLE;
		}
		return new SeatView(seat.getId(), seat.getSeatCode(), seat.getLocation(), state, null, null);
	}
}
