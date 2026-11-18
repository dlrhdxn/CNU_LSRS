package com.cnu.lsrs.service.dto;

import com.cnu.lsrs.domain.CancelReason;
import com.cnu.lsrs.domain.Reservation;
import com.cnu.lsrs.domain.ReservationStatus;

import java.time.LocalDateTime;

/** 예약 생성(US-04)·취소(US-05) 응답. qrToken은 C(US-07)의 QR 화면에서 쓴다 */
public record ReservationResult(Long reservationId, String seatCode, LocalDateTime startAt, LocalDateTime endAt,
								ReservationStatus status, String qrToken, CancelReason cancelReason) {

	public static ReservationResult from(Reservation r) {
		return new ReservationResult(r.getId(), r.getSeat().getSeatCode(), r.getStartAt(), r.getEndAt(),
				r.getStatus(), r.getQrToken(), r.getCancelReason());
	}
}
