package com.cnu.lsrs.security;

import com.cnu.lsrs.domain.Role;
import com.cnu.lsrs.domain.User;
import com.cnu.lsrs.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * URL별 권한 규칙 검증 (FR-U-12).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityRulesTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	@BeforeEach
	void setUp() {
		userRepository.deleteAll();
		String hash = passwordEncoder.encode("Test1234!");
		userRepository.save(User.createActive("20230001", "s1@lsrs.test", hash, Role.STUDENT));
		userRepository.save(User.createPendingStudent("20230003", "p@lsrs.test", hash, "123456"));
	}

	@Test
	void 비로그인으로_학생_API를_부르면_401_JSON() throws Exception {
		mockMvc.perform(get("/api/seats"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void 회원가입과_로그인_API는_비로그인도_통과한다() throws Exception {
		mockMvc.perform(post("/api/auth/register")).andExpect(status().isNotFound());
		mockMvc.perform(post("/api/auth/login")).andExpect(status().isNotFound());
	}

	@Test
	void 홈_화면은_누구나_볼_수_있다() throws Exception {
		mockMvc.perform(get("/")).andExpect(status().isOk());
	}

	@Test
	@WithMockUser(roles = "STUDENT")
	void 학생은_관리자_API와_체크인_API에_접근할_수_없다() throws Exception {
		mockMvc.perform(get("/api/admin/seats"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		mockMvc.perform(post("/api/check-in")).andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(roles = "STUDENT")
	void 학생은_좌석_예약_API_권한을_통과한다() throws Exception {
		mockMvc.perform(get("/api/seats")).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/reservations/me")).andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser(roles = "KIOSK")
	void 체크인_단말은_체크인만_가능하다() throws Exception {
		mockMvc.perform(post("/api/check-in")).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/seats")).andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void 관리자는_관리자와_학생_기능_API에_모두_접근할_수_있다() throws Exception {
		mockMvc.perform(get("/api/admin/seats")).andExpect(status().isOk());
		mockMvc.perform(get("/api/admin/users"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].role").value("STUDENT"))
				.andExpect(jsonPath("$[0].passwordHash").doesNotExist())
				.andExpect(jsonPath("$[0].verificationCode").doesNotExist());
		mockMvc.perform(get("/api/admin/statistics")
						.param("startAt", "2026-11-15T00:00:00")
						.param("endAt", "2026-11-16T00:00:00"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.points").isArray())
				.andExpect(jsonPath("$.averageUtilizationRate").exists());
		mockMvc.perform(delete("/api/admin/policies/exam")).andExpect(status().isNoContent());
		mockMvc.perform(get("/api/seats")).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/reservations/me")).andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void 관리자는_학생의_노쇼_횟수를_초기화할_수_있다() throws Exception {
		User student = userRepository.findByStudentNo("20230001").orElseThrow();

		mockMvc.perform(patch("/api/admin/{userId}/no-show-count", student.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value(student.getId()))
				.andExpect(jsonPath("$.noShowCount").value(0));
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void 관리자는_사용자_역할을_변경할_수_있다() throws Exception {
		User student = userRepository.findByStudentNo("20230001").orElseThrow();

		mockMvc.perform(patch("/api/admin/{userId}/role", student.getId())
						.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
						.content("{\"role\":\"ADMIN\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("ADMIN"));
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void 관리자_대시보드_화면이_렌더링된다() throws Exception {
		mockMvc.perform(get("/admin")).andExpect(status().isOk());
	}

	@Test
	@WithMockUser(roles = "KIOSK")
	void 체크인_페이지는_없고_API만_사용한다() throws Exception {
		mockMvc.perform(get("/kiosk/check-in")).andExpect(status().isNotFound());
	}

	@Test
	void 실제_계정으로_Basic_로그인하면_인증된다() throws Exception {
		mockMvc.perform(get("/api/seats").with(httpBasic("20230001", "Test1234!")))
				.andExpect(status().isNotFound()); // 인증·권한 통과 → 아직 컨트롤러 없음
	}

	@Test
	void 비밀번호가_틀리면_401() throws Exception {
		mockMvc.perform(get("/api/seats").with(httpBasic("20230001", "wrong")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 이메일_인증_전_PENDING_계정은_로그인할_수_없다() throws Exception {
		mockMvc.perform(get("/api/seats").with(httpBasic("20230003", "Test1234!")))
				.andExpect(status().isUnauthorized());
	}
}
