package com.cnu.lsrs.domain;

/**
 * 예약 상태 (FR-R-13, FR-C-03, FR-X-10).
 * 상태 전이: RESERVED → CHECKED_IN | CANCELLED | AUTO_CANCELLED, CHECKED_IN → CANCELLED(관리자 강제 취소)
 */
public enum ReservationStatus {
	RESERVED,
	CHECKED_IN,
	CANCELLED,
	/** 15분 내 미체크인으로 스케줄러가 취소한 노쇼 */
	AUTO_CANCELLED;

	/** 좌석을 점유하는 활성 예약인지 (겹침 검사 대상) */
	public boolean isActive() {
		return this == RESERVED || this == CHECKED_IN;
	}
}
