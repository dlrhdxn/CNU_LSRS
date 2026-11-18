package com.cnu.lsrs.service.dto;

import com.cnu.lsrs.domain.ReservationStatus;

/**
 * 조회한 시간대의 좌석 하나 (US-03).
 * myReservationId는 state가 MINE일 때만 채워진다 → 화면에서 바로 취소(US-05)할 때 사용.
 */
public record SeatView(Long seatId, String seatCode, String location, SeatState state,
					   Long myReservationId, ReservationStatus myReservationStatus) {

	/** FR-R-02의 4가지 상태 + 관리자가 막아 둔 좌석 */
	public enum SeatState {
		/** 예약 가능 */
		AVAILABLE,
		/** 사용 중: 겹치는 예약이 체크인됨 */
		IN_USE,
		/** 내 예약 */
		MINE,
		/** 예약됨: 다른 학생의 체크인 전 예약 */
		RESERVED,
		/** 관리자가 사용 불가로 지정 (US-04) */
		UNAVAILABLE
	}
}
