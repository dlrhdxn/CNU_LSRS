package com.cnu.lsrs.controller;

import com.cnu.lsrs.security.LoginUser;
import com.cnu.lsrs.service.SeatService;
import com.cnu.lsrs.service.dto.SeatAvailability;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;

/** 좌석 현황 조회 API (US-03) */
@RestController
@RequestMapping("/api/seats")
public class SeatController {

	private final SeatService seatService;

	public SeatController(SeatService seatService) {
		this.seatService = seatService;
	}

	/** GET /api/seats?date=2026-11-19&startAt=10:00&endAt=12:00 */
	@GetMapping
	public SeatAvailability getSeats(@AuthenticationPrincipal LoginUser loginUser,
									 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
									 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startAt,
									 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endAt) {
		return seatService.getAvailability(loginUser.getUserId(), date, startAt, endAt);
	}
}
