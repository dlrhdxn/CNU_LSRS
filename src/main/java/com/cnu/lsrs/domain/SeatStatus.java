package com.cnu.lsrs.domain;

/** 좌석 자체의 운영 상태. 예약 여부와는 별개이며 UNAVAILABLE 좌석은 예약할 수 없다 (US-04). */
public enum SeatStatus {
	AVAILABLE,
	UNAVAILABLE
}
