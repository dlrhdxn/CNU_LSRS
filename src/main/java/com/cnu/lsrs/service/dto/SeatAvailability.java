package com.cnu.lsrs.service.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * GET /api/seats 응답 (US-03).
 * bookable=false면 notice에 정책 위반 사유가 들어 있고, 화면은 예약 버튼을 막는다.
 */
public record SeatAvailability(LocalDate date, LocalTime startAt, LocalTime endAt,
							   PolicyInfo policy, boolean bookable, String notice, List<SeatView> seats) {
}
