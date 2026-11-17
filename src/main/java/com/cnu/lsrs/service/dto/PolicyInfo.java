package com.cnu.lsrs.service.dto;

import com.cnu.lsrs.domain.PeriodType;
import com.cnu.lsrs.domain.ReservationPolicy;

import java.time.LocalDate;

/** 좌석 화면에 표시할 현재 예약 정책 (FR-R-03: 시험기간이면 최대 3시간을 UI에 명시) */
public record PolicyInfo(PeriodType periodType, int bookingDays, int maxHours, LocalDate lastBookableDate) {

	public static PolicyInfo of(ReservationPolicy policy, LocalDate today) {
		return new PolicyInfo(policy.getPeriodType(), policy.getBookingDays(), policy.getMaxHours(),
				today.plusDays(policy.getBookingDays()));
	}
}
