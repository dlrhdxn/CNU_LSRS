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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * URL별 권한 규칙 검증 (FR-U-12).
 * 아직 기능 컨트롤러가 없으므로, 권한을 통과하면 404(경로 없음), 막히면 401/403이 나온다.
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
		mockMvc.perform(get("/api/admin/dashboard"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		mockMvc.perform(post("/api/check-ins")).andExpect(status().isForbidden());
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
		mockMvc.perform(post("/api/check-ins")).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/seats")).andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void 관리자는_관리자_API_권한을_통과하지만_학생_API는_막힌다() throws Exception {
		mockMvc.perform(get("/api/admin/dashboard")).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/seats")).andExpect(status().isForbidden());
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
