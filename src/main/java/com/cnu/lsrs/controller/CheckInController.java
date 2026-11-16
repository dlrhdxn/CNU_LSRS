package com.cnu.lsrs.controller;

import com.cnu.lsrs.domain.ReservationStatus;
import com.cnu.lsrs.service.CheckInService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/check-in")
public class CheckInController {

	private final CheckInService checkInService;

	public CheckInController(CheckInService checkInService) {
		this.checkInService = checkInService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.OK)
	public CheckInResponse checkIn(@Valid @RequestBody CheckInRequest request) {
		CheckInService.CheckInResult result = checkInService.checkIn(request.seatCode(), request.qrToken());
		return new CheckInResponse(result.reservationId(), result.seatCode(), result.status(), result.checkedInAt());
	}

	public record CheckInRequest(@NotBlank String seatCode, @NotBlank String qrToken) {
	}

	public record CheckInResponse(Long reservationId, String seatCode, ReservationStatus status,
								  LocalDateTime checkedInAt) {
	}
}
