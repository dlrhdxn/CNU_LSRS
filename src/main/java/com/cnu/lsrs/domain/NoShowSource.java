package com.cnu.lsrs.domain;

/** 노쇼 기록 출처. AUTO: 15분 미체크인 자동 처리, ADMIN: 관리자 강제 취소 시 패널티 부여 (FR-X-22) */
public enum NoShowSource {
	AUTO,
	ADMIN
}
