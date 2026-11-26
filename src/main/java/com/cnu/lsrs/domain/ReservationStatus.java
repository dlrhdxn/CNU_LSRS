package com.cnu.lsrs.domain;

/**
 * 예약 상태 (FR-R-13, FR-C-03, FR-X-10).
 * 상태 전이: RESERVED → CHECKED_IN | CANCELLED | AUTO_CANCELLED, CHECKED_IN → CANCELLED(관리자 강제 취소)
 */
public enum ReservationStatus {
	RESERVED("예약중"),
	CHECKED_IN("체크인 완료"),
	CANCELLED("취소됨"),
	/** 15분 내 미체크인으로 스케줄러가 취소한 노쇼 */
	AUTO_CANCELLED("노쇼");

	/** 화면 표시용 이름 (US-06: 예약중·체크인 완료·취소됨·노쇼) */
	private final String label;

	ReservationStatus(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	/** 좌석을 점유하는 활성 예약인지 (겹침 검사 대상) */
	public boolean isActive() {
		return this == RESERVED || this == CHECKED_IN;
	}
}
