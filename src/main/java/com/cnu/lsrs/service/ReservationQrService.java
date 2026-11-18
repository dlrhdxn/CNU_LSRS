package com.cnu.lsrs.service;

import com.cnu.lsrs.common.exception.BusinessException;
import com.cnu.lsrs.domain.Reservation;
import com.cnu.lsrs.domain.ReservationStatus;
import com.cnu.lsrs.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReservationQrService {

	private final ReservationRepository reservationRepository;

	public ReservationQrService(ReservationRepository reservationRepository) {
		this.reservationRepository = reservationRepository;
	}

	public QrDetails getQr(Long reservationId, Long userId) {
		Reservation reservation = findOwnedReservation(reservationId, userId);
		return new QrDetails(reservation.getSeat().getSeatCode(), reservation.getQrToken());
	}

	private Reservation findOwnedReservation(Long reservationId, Long userId) {
		Reservation reservation = reservationRepository.findById(reservationId)
				.orElseThrow(() -> BusinessException.notFound("RESERVATION_NOT_FOUND", "예약을 찾을 수 없습니다."));
		if (!reservation.isOwnedBy(userId)) {
			throw BusinessException.forbidden("NOT_RESERVATION_OWNER", "본인의 예약 QR만 확인할 수 있습니다.");
		}
		if (reservation.getStatus() != ReservationStatus.RESERVED) {
			throw BusinessException.conflict("QR_NOT_AVAILABLE", "체크인 대기 중인 예약만 QR을 발급할 수 있습니다.");
		}
		return reservation;
	}

	public record QrDetails(String seatCode, String qrToken) {
	}
}
