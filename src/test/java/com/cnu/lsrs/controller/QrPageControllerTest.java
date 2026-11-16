package com.cnu.lsrs.controller;

import com.cnu.lsrs.domain.CancelReason;
import com.cnu.lsrs.domain.Reservation;
import com.cnu.lsrs.domain.Role;
import com.cnu.lsrs.domain.Seat;
import com.cnu.lsrs.domain.User;
import com.cnu.lsrs.repository.NoShowRecordRepository;
import com.cnu.lsrs.repository.ReservationRepository;
import com.cnu.lsrs.repository.SeatRepository;
import com.cnu.lsrs.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class QrPageControllerTest {

	private static final String PASSWORD = "Test1234!";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	SeatRepository seatRepository;

	@Autowired
	ReservationRepository reservationRepository;

	@Autowired
	NoShowRecordRepository noShowRecordRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	Clock clock;

	private Long ownerId;

	@BeforeEach
	void setUp() {
		noShowRecordRepository.deleteAll();
		reservationRepository.deleteAll();
		seatRepository.deleteAll();
		userRepository.deleteAll();
		userRepository.flush();
		String hash = passwordEncoder.encode(PASSWORD);
		User owner = userRepository.save(User.createActive("20230001", "owner@lsrs.test", hash, Role.STUDENT));
		userRepository.save(User.createActive("20230002", "other@lsrs.test", hash, Role.STUDENT));
		ownerId = owner.getId();
	}

	@Test
	void 예약_소유자는_한_페이지에서_QR_이미지를_확인할_수_있다() throws Exception {
		Reservation reservation = createReservation("owner-qr-token");

		mockMvc.perform(get("/reservations/{id}/qr", reservation.getId())
						.with(httpBasic("20230001", PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("좌석: A-01")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("data:image/png;base64,")))
				.andExpect(content().string(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("/qr/image"))))
				.andExpect(header().string("Cache-Control", "no-store"));

		mockMvc.perform(get("/api/reservations/{id}/qr", reservation.getId())
						.with(httpBasic("20230001", PASSWORD)))
				.andExpect(status().isNotFound());
	}

	@Test
	void 다른_학생은_예약_QR을_조회할_수_없다() throws Exception {
		Reservation reservation = createReservation("private-qr-token");

		mockMvc.perform(get("/reservations/{id}/qr", reservation.getId())
						.with(httpBasic("20230002", PASSWORD)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("NOT_RESERVATION_OWNER"));
	}

	@Test
	void 취소된_예약의_QR은_발급하지_않는다() throws Exception {
		Reservation reservation = createReservation("cancelled-qr-token");
		reservation.cancelByAdmin(CancelReason.ADMIN_OTHER);
		reservationRepository.save(reservation);

		mockMvc.perform(get("/reservations/{id}/qr", reservation.getId())
						.with(httpBasic("20230001", PASSWORD)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("QR_NOT_AVAILABLE"));
	}

	private Reservation createReservation(String qrToken) {
		LocalDateTime now = LocalDateTime.now(clock);
		User owner = userRepository.findById(ownerId).orElseThrow();
		Seat seat = seatRepository.save(Seat.create("A-01", "1층 열람실 A"));
		return reservationRepository.save(Reservation.create(owner, seat, now.plusMinutes(1),
				now.plusHours(2), qrToken, now));
	}
}
