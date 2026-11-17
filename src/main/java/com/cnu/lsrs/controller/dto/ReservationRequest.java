package com.cnu.lsrs.controller.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/** POST /api/reservations 요청 (US-04). 예: {"seatId": 1, "date": "2026-11-19", "startAt": "10:00", "endAt": "12:00"} */
public record ReservationRequest(
		@NotNull(message = "좌석을 선택해 주세요.") Long seatId,
		@NotNull(message = "날짜를 선택해 주세요.") LocalDate date,
		@NotNull(message = "시작 시각을 선택해 주세요.") LocalTime startAt,
		@NotNull(message = "종료 시각을 선택해 주세요.") LocalTime endAt
) {
}
