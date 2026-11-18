package com.cnu.lsrs.controller;

import com.cnu.lsrs.security.LoginUser;
import com.cnu.lsrs.service.QrCodeImageGenerator;
import com.cnu.lsrs.service.ReservationQrService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Base64;

@Controller
public class QrPageController {

	private final ReservationQrService reservationQrService;
	private final QrCodeImageGenerator imageGenerator;

	public QrPageController(ReservationQrService reservationQrService, QrCodeImageGenerator imageGenerator) {
		this.reservationQrService = reservationQrService;
		this.imageGenerator = imageGenerator;
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
}
