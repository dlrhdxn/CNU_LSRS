package com.cnu.lsrs.domain;

/** 계정 상태 (FR-U-03, FR-U-04). PENDING 계정은 로그인할 수 없다. */
public enum AccountStatus {
	PENDING,
	ACTIVE
}
