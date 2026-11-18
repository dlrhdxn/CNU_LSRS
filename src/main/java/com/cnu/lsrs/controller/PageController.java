package com.cnu.lsrs.controller;

import com.cnu.lsrs.domain.CancelReason;
import com.cnu.lsrs.security.LoginUser;
import com.cnu.lsrs.service.QrCodeImageGenerator;
import com.cnu.lsrs.service.ReservationQrService;
import com.cnu.lsrs.service.ReservationService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.ui.Model;

import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;

/** Thymeleaf 화면 라우팅을 담당한다. 업무 데이터는 서비스 계층에서 조회한다. */
@Controller
public class PageController {

	private final ReservationService reservationService;
	private final ReservationQrService reservationQrService;
	private final QrCodeImageGenerator imageGenerator;
	private final Clock clock;

	public PageController(ReservationService reservationService, ReservationQrService reservationQrService,
						  QrCodeImageGenerator imageGenerator, Clock clock) {
		this.reservationService = reservationService;
		this.reservationQrService = reservationQrService;
		this.imageGenerator = imageGenerator;
		this.clock = clock;
	}

	@GetMapping("/")
	public String home() {
		return "index";
	}

	@GetMapping("/login")
	public String login() {
		return "login";
	}

	@GetMapping("/signup")
	public String signup() {
		return "signup";
	}

	@GetMapping("/verify-email")
	public String verifyEmail() {
		return "verify-email";
	}

	@GetMapping("/seats")
	public String seats(Model model) {
		model.addAttribute("cancelReasons",
				Arrays.stream(CancelReason.values()).filter(reason -> !reason.isAdminReason()).toList());
		return "seats";
	}

	@GetMapping("/reservations/me")
	public String myReservations(@AuthenticationPrincipal LoginUser loginUser, Model model) {
		model.addAttribute("reservations", reservationService.getMyReservations(loginUser.getUserId()));
		model.addAttribute("nowEpochMillis", clock.millis());
		return "reservations/me";
	}

	@GetMapping("/reservations/{reservationId}/qr")
	public String qr(@PathVariable Long reservationId,
					 @AuthenticationPrincipal LoginUser loginUser, Model model,
					 HttpServletResponse response) {
		ReservationQrService.QrDetails qr = reservationQrService.getQr(reservationId, loginUser.getUserId());
		model.addAttribute("seatCode", qr.seatCode());
		model.addAttribute("qrImage", Base64.getEncoder()
				.encodeToString(imageGenerator.generate(qr.seatCode(), qr.qrToken())));
		response.setHeader("Cache-Control", "no-store");
		return "reservations/qr";
	}

	@GetMapping("/admin")
	public String dashboard() {
		return "admin/dashboard";
	}
}
