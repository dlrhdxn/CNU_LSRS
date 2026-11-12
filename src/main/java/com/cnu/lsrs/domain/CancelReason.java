package com.cnu.lsrs.domain;

/** 취소 사유. 학생 직접 취소(FR-X-02)와 관리자 강제 취소(FR-X-21)를 구분한다. */
public enum CancelReason {
	// 학생 직접 취소 (FR-X-02)
	SCHEDULE_CHANGE("일정 변경", false),
	SEAT_CHANGE("좌석 위치 변경", false),
	TIME_CHANGE("시간대 변경", false),
	STUDENT_OTHER("기타", false),

	// 관리자 강제 취소 (FR-X-21)
	FACILITY_INSPECTION("시설 긴급 점검", true),
	RULE_VIOLATION("이용 규정 위반", true),
	SYSTEM_CORRECTION("시스템 오류 보정", true),
	ADMIN_OTHER("기타", true);

	private final String label;
	private final boolean adminReason;

	CancelReason(String label, boolean adminReason) {
		this.label = label;
		this.adminReason = adminReason;
	}

	public String getLabel() {
		return label;
	}

	public boolean isAdminReason() {
		return adminReason;
	}
}
