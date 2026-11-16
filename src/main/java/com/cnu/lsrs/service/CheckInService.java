package com.cnu.lsrs.service;

import com.cnu.lsrs.common.exception.BusinessException;
import com.cnu.lsrs.config.LsrsProperties;
import com.cnu.lsrs.domain.Reservation;
import com.cnu.lsrs.domain.ReservationStatus;
import com.cnu.lsrs.repository.ReservationRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class CheckInService {

	private final ReservationRepository reservationRepository;
	private final LsrsProperties properties;
	private final Clock clock;

	public CheckInService(ReservationRepository reservationRepository, LsrsProperties properties, Clock clock) {
		this.reservationRepository = reservationRepository;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional
	public CheckInResult checkIn(String seatCode, String qrToken) {
		if (seatCode == null || seatCode.isBlank() || qrToken == null || qrToken.isBlank()) {
			throw BusinessException.badRequest("INVALID_QR", "좌석 정보와 QR 토큰이 필요합니다.");
		}

		Reservation reservation = reservationRepository.findByQrTokenForUpdate(qrToken)
				.orElseThrow(() -> BusinessException.notFound("QR_NOT_FOUND", "유효한 예약 QR이 아닙니다."));
		if (!reservation.getSeat().getSeatCode().equals(seatCode)) {
			throw BusinessException.badRequest("QR_SEAT_MISMATCH", "QR의 좌석 정보가 예약과 일치하지 않습니다.");
		}
		if (reservation.getStatus() != ReservationStatus.RESERVED) {
			throw BusinessException.conflict("RESERVATION_NOT_CHECK_IN_READY", "체크인 가능한 예약 상태가 아닙니다.");
		}

		LocalDateTime now = LocalDateTime.now(clock);
		if (now.isBefore(reservation.getStartAt())) {
			throw BusinessException.conflict("CHECK_IN_NOT_STARTED", "예약 시작 시각 이후에 체크인할 수 있습니다.");
		}
		if (now.isAfter(reservation.getStartAt().plus(properties.policy().checkInWindow()))) {
			throw BusinessException.conflict("CHECK_IN_WINDOW_EXPIRED", "체크인 허용 시간이 지났습니다.");
		}

		reservation.checkIn(now);
		return new CheckInResult(reservation.getId(), reservation.getSeat().getSeatCode(),
				reservation.getStatus(), reservation.getCheckedInAt());
	}

	public record CheckInResult(Long reservationId, String seatCode, ReservationStatus status,
								LocalDateTime checkedInAt) {
	}
}
