package com.cnu.lsrs.controller;

import com.cnu.lsrs.controller.dto.ReservationRequest;
import com.cnu.lsrs.domain.CancelReason;
import com.cnu.lsrs.security.LoginUser;
import com.cnu.lsrs.service.ReservationService;
import com.cnu.lsrs.service.dto.ReservationResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 좌석 예약(US-04)·취소(US-05) API. 내 예약 조회(US-06)는 Sprint 2에 여기에 추가한다. */
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

	private final ReservationService reservationService;

	public ReservationController(ReservationService reservationService) {
		this.reservationService = reservationService;
	}

	/** POST /api/reservations → 201, 예약 정보와 qrToken */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ReservationResult reserve(@AuthenticationPrincipal LoginUser loginUser,
									 @Valid @RequestBody ReservationRequest request) {
		return reservationService.reserve(loginUser.getUserId(), request.seatId(), request.date(),
				request.startAt(), request.endAt());
	}

	/** DELETE /api/reservations/{id}?reason=SCHEDULE_CHANGE → 200, 취소된 예약 정보 */
	@DeleteMapping("/{id}")
	public ReservationResult cancel(@AuthenticationPrincipal LoginUser loginUser,
									@PathVariable Long id,
									@RequestParam CancelReason reason) {
		return reservationService.cancel(loginUser.getUserId(), id, reason);
	}
}
