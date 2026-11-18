package com.cnu.lsrs.controller;

import com.cnu.lsrs.domain.CancelReason;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Arrays;

/** 좌석 조회·예약 화면 (US-03~05). 데이터는 화면의 JS가 /api/seats, /api/reservations로 가져온다. */
@Controller
public class SeatPageController {

	@GetMapping("/seats")
	public String seats(Model model) {
		model.addAttribute("cancelReasons",
				Arrays.stream(CancelReason.values()).filter(r -> !r.isAdminReason()).toList());
		return "seats";
	}
}
