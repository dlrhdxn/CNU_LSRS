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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * URL별 권한 규칙 검증 (FR-U-12).
 * 권한을 통과하면 401/403이 아닌 응답(아직 없는 경로는 404, 파라미터가 빠지면 400 등), 막히면 401/403이 나온다.
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

	@MockBean
	com.cnu.lsrs.service.MailService mailService;

	/** 인증·권한 필터는 통과했다 (401·403이 아니다). 그 뒤 컨트롤러 응답 코드는 상관없다 */
	private static ResultMatcher passesSecurity() {
		return result -> {
			int code = result.getResponse().getStatus();
			if (code == 401 || code == 403) {
				throw new AssertionError("권한 검사에서 막혔습니다: " + code);
			}
		};
	}

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
		mockMvc.perform(post("/api/auth/register")).andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/auth/login")).andExpect(status().isBadRequest());
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
		mockMvc.perform(get("/api/seats")).andExpect(passesSecurity());
		mockMvc.perform(get("/api/reservations/me")).andExpect(passesSecurity());
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
		mockMvc.perform(get("/api/seats")).andExpect(passesSecurity());
		mockMvc.perform(get("/api/reservations/me")).andExpect(passesSecurity());
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
				.andExpect(passesSecurity());
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

	@Test
	void 회원가입_후_이메일_인증하면_로그인할_수_있다() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
						.content("{\"studentNo\":\"20239999\",\"email\":\"new@o.cnu.ac.kr\",\"password\":\"Test1234!\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.loginId").value("20239999"))
				.andExpect(jsonPath("$.accountStatus").value("PENDING"));

		User pending = userRepository.findByStudentNo("20239999").orElseThrow();
		mockMvc.perform(post("/api/auth/email-verification")
						.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
						.content("{\"studentNo\":\"20239999\",\"code\":\"" + pending.getVerificationCode() + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accountStatus").value("ACTIVE"));

		mockMvc.perform(post("/api/auth/login")
						.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
						.content("{\"loginId\":\"20239999\",\"password\":\"Test1234!\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("STUDENT"))
				.andExpect(header().exists("Set-Cookie"));
	}

	@Test
	void PENDING_계정은_JWT_로그인을_할_수_없다() throws Exception {
		mockMvc.perform(post("/api/auth/login")
						.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
						.content("{\"loginId\":\"20230003\",\"password\":\"Test1234!\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("ACCOUNT_PENDING"));
	}

	@Test
	void PENDING_계정도_비밀번호가_틀리면_인증_화면_이동_정보를_받지_않는다() throws Exception {
		mockMvc.perform(post("/api/auth/login")
						.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
						.content("{\"loginId\":\"20230003\",\"password\":\"wrong-password\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void 회원가입은_충남대_이메일_도메인만_허용한다() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
						.content("{\"studentNo\":\"20239998\",\"email\":\"student@gmail.com\",\"password\":\"Test1234!\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_EMAIL_DOMAIN"));
	}
}
