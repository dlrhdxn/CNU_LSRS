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
import com.cnu.lsrs.support.FixedClockConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.cnu.lsrs.support.FixedClockConfig.NOW;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-06 내 예약 및 이용 이력 조회의 Acceptance Criteria.
 * 시각은 2026-11-19 09:00으로 고정 (FixedClockConfig).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FixedClockConfig.class)
@Transactional
class MyReservationsPageTest {

	private static final String PASSWORD = "Test1234!";

	@Autowired MockMvc mockMvc;
	@Autowired UserRepository userRepository;
	@Autowired SeatRepository seatRepository;
	@Autowired ReservationRepository reservationRepository;
	@Autowired NoShowRecordRepository noShowRecordRepository;
	@Autowired PasswordEncoder passwordEncoder;

	User owner;
	User other;

	@BeforeEach
	void setUp() {
		noShowRecordRepository.deleteAll();
		reservationRepository.deleteAll();
		seatRepository.deleteAll();
		userRepository.deleteAll();
		String hash = passwordEncoder.encode(PASSWORD);
		owner = userRepository.save(User.createActive("20230001", "owner@lsrs.test", hash, Role.STUDENT));
		other = userRepository.save(User.createActive("20230002", "other@lsrs.test", hash, Role.STUDENT));
	}

	private Reservation reserve(User user, String seatCode, LocalDateTime startAt) {
		Seat seat = seatRepository.save(Seat.create(seatCode, "1층"));
		return reservationRepository.save(Reservation.create(user, seat, startAt, startAt.plusHours(1),
				UUID.randomUUID().toString(), NOW.minusDays(1)));
	}

	@Test
	void 내_예약_내역에_예약중_체크인_완료_취소됨_노쇼_상태가_표시된다() throws Exception {
		reserve(owner, "A-01", NOW.plusHours(2));                       // RESERVED
		reserve(owner, "A-02", NOW.minusMinutes(30)).checkIn(NOW.minusMinutes(25));
		reserve(owner, "A-03", NOW.plusHours(4)).cancelByStudent(CancelReason.SCHEDULE_CHANGE);
		reserve(owner, "A-04", NOW.minusHours(2)).autoCancel();        // 스케줄러가 처리한 노쇼
		reservationRepository.flush();

		mockMvc.perform(get("/reservations/me").with(httpBasic("20230001", PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("<span>예약중</span>")))
				.andExpect(content().string(containsString("<span>체크인 완료</span>")))
				.andExpect(content().string(containsString("<span>취소됨</span>")))
				.andExpect(content().string(containsString("<span>노쇼</span>")))
				.andExpect(content().string(not(containsString("AUTO_CANCELLED"))));
	}

	@Test
	void 다른_학생의_예약은_보이지_않는다() throws Exception {
		reserve(owner, "A-01", NOW.plusHours(2));
		reserve(other, "B-09", NOW.plusHours(2));

		mockMvc.perform(get("/reservations/me").with(httpBasic("20230001", PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("좌석 A-01")))
				.andExpect(content().string(not(containsString("B-09"))));
	}

	@Test
	void 로그인하지_않으면_내_예약을_볼_수_없다() throws Exception {
		mockMvc.perform(get("/reservations/me"))
				.andExpect(status().is3xxRedirection());
	}
}
