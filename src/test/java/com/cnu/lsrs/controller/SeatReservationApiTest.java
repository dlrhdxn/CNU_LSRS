package com.cnu.lsrs.controller;

import com.cnu.lsrs.domain.ReservationPolicy;
import com.cnu.lsrs.domain.Role;
import com.cnu.lsrs.domain.Seat;
import com.cnu.lsrs.domain.User;
import com.cnu.lsrs.repository.NoShowRecordRepository;
import com.cnu.lsrs.repository.ReservationPolicyRepository;
import com.cnu.lsrs.repository.ReservationRepository;
import com.cnu.lsrs.repository.SeatRepository;
import com.cnu.lsrs.repository.UserRepository;
import com.cnu.lsrs.support.FixedClockConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-03~05 API: 실제 계정 로그인 → 조회 → 예약 → 취소 흐름과 오류 응답 형식 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FixedClockConfig.class)
class SeatReservationApiTest {

	private static final String PW = "Test1234!";

	@Autowired MockMvc mockMvc;
	@Autowired ObjectMapper objectMapper;
	@Autowired PasswordEncoder passwordEncoder;
	@Autowired ReservationRepository reservationRepository;
	@Autowired SeatRepository seatRepository;
	@Autowired UserRepository userRepository;
	@Autowired ReservationPolicyRepository policyRepository;
	@Autowired NoShowRecordRepository noShowRecordRepository;

	Seat a01;

	@BeforeEach
	void setUp() {
		noShowRecordRepository.deleteAll();
		reservationRepository.deleteAll();
		seatRepository.deleteAll();
		userRepository.deleteAll();
		policyRepository.deleteAll();

		policyRepository.save(ReservationPolicy.normal(7, 4));
		String hash = passwordEncoder.encode(PW);
		userRepository.save(User.createActive("20230001", "s1@lsrs.test", hash, Role.STUDENT));
		userRepository.save(User.createActive("20230002", "s2@lsrs.test", hash, Role.STUDENT));
		userRepository.save(User.createActive("kiosk1", "k@lsrs.test", hash, Role.KIOSK));
		a01 = seatRepository.save(Seat.create("A-01", "1층"));
	}

	private static RequestPostProcessor as(String loginId) {
		return httpBasic(loginId, PW);
	}

	private String body(String start, String end) {
		return """
				{"seatId": %d, "date": "2026-11-19", "startAt": "%s", "endAt": "%s"}""".formatted(a01.getId(), start, end);
	}

	@Test
	void 조회_예약_취소_흐름() throws Exception {
		mockMvc.perform(get("/api/seats").with(as("20230001"))
						.param("date", "2026-11-19").param("startAt", "10:00").param("endAt", "12:00"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bookable").value(true))
				.andExpect(jsonPath("$.policy.maxHours").value(4))
				.andExpect(jsonPath("$.seats[0].state").value("AVAILABLE"));

		String created = mockMvc.perform(post("/api/reservations").with(as("20230001"))
						.contentType(MediaType.APPLICATION_JSON).content(body("10:00", "12:00")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("RESERVED"))
				.andExpect(jsonPath("$.seatCode").value("A-01"))
				.andExpect(jsonPath("$.qrToken").isNotEmpty())
				.andReturn().getResponse().getContentAsString();
		long id = objectMapper.readTree(created).get("reservationId").asLong();

		mockMvc.perform(get("/api/seats").with(as("20230002"))
						.param("date", "2026-11-19").param("startAt", "11:00").param("endAt", "12:00"))
				.andExpect(jsonPath("$.seats[0].state").value("RESERVED"));

		mockMvc.perform(delete("/api/reservations/{id}", id).with(as("20230001")).param("reason", "SCHEDULE_CHANGE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));

		mockMvc.perform(get("/api/seats").with(as("20230002"))
						.param("date", "2026-11-19").param("startAt", "11:00").param("endAt", "12:00"))
				.andExpect(jsonPath("$.seats[0].state").value("AVAILABLE"));
	}

	@Test
	void 이미_예약된_좌석은_409와_안내_메시지() throws Exception {
		mockMvc.perform(post("/api/reservations").with(as("20230001"))
				.contentType(MediaType.APPLICATION_JSON).content(body("10:00", "12:00")));

		mockMvc.perform(post("/api/reservations").with(as("20230002"))
						.contentType(MediaType.APPLICATION_JSON).content(body("11:00", "12:00")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SEAT_ALREADY_RESERVED"))
				.andExpect(jsonPath("$.message").isNotEmpty());
	}

	@Test
	void 필수값이_빠진_예약_요청은_400() throws Exception {
		mockMvc.perform(post("/api/reservations").with(as("20230001"))
						.contentType(MediaType.APPLICATION_JSON).content("{\"date\": \"2026-11-19\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	void 다른_학생의_예약_취소는_403() throws Exception {
		String created = mockMvc.perform(post("/api/reservations").with(as("20230001"))
						.contentType(MediaType.APPLICATION_JSON).content(body("10:00", "12:00")))
				.andReturn().getResponse().getContentAsString();
		JsonNode node = objectMapper.readTree(created);

		mockMvc.perform(delete("/api/reservations/{id}", node.get("reservationId").asLong())
						.with(as("20230002")).param("reason", "STUDENT_OTHER"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("RESERVATION_FORBIDDEN"));
	}

	@Test
	void 체크인_단말_계정은_좌석_조회와_예약을_할_수_없다() throws Exception {
		mockMvc.perform(get("/api/seats").with(as("kiosk1"))
						.param("date", "2026-11-19").param("startAt", "10:00").param("endAt", "12:00"))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/reservations").with(as("kiosk1"))
						.contentType(MediaType.APPLICATION_JSON).content(body("10:00", "12:00")))
				.andExpect(status().isForbidden());
	}

	@Test
	void 좌석_화면은_학생이_열_수_있다() throws Exception {
		mockMvc.perform(get("/seats").with(as("20230001"))).andExpect(status().isOk());
	}
}
