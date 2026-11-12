package com.cnu.lsrs.config;

import com.cnu.lsrs.common.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;

import java.io.IOException;

/**
 * URL별 접근 권한 (FR-U-12, NFR 4.5). 설계서 4장 API 목록 기준으로 Sprint 1·2 경로를 모두 미리 정해 둔다.
 *
 * [임시] 지금은 HTTP Basic 인증으로 테스트 계정 로그인이 된다 (Postman: Authorization → Basic Auth).
 * A(E1 인증 담당)가 JwtAuthFilter를 완성하면:
 *   1) httpBasic(...) 줄을 지우고 JwtAuthFilter를 addFilterBefore로 등록
 *   2) 화면 요청의 미인증 처리(pageEntryPoint)를 /login 리다이렉트로 변경
 * 나머지 URL 규칙은 그대로 둔다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final ObjectMapper objectMapper;

	/** [임시] 화면 요청 미인증 시 브라우저 Basic 로그인 창. A가 로그인 화면을 만들면 /login 리다이렉트로 교체 */
	private final AuthenticationEntryPoint pageEntryPoint;

	public SecurityConfig(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
		BasicAuthenticationEntryPoint basic = new BasicAuthenticationEntryPoint();
		basic.setRealmName("LSRS");
		this.pageEntryPoint = basic;
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(); // NFR 4.5
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				// JWT 기반 무상태 인증이라 서버 세션과 CSRF 토큰을 쓰지 않는다.
				// (JWT 쿠키는 SameSite 설정으로 교차 사이트 전송을 막는다 - A 담당)
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.formLogin(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable) // 로그아웃은 POST /api/auth/logout (A 담당)
				.httpBasic(Customizer.withDefaults()) // [임시] JWT 완성 전까지 사용
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint(this::handleUnauthenticated)
						.accessDeniedHandler(accessDeniedHandler()))
				.authorizeHttpRequests(auth -> auth
						// 정적 리소스·오류 페이지
						.requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
						.requestMatchers("/error").permitAll()

						// E1 회원·인증 (US-01, US-02): 비로그인 허용
						.requestMatchers(HttpMethod.POST,
								"/api/auth/register", "/api/auth/email-verification", "/api/auth/login").permitAll()
						.requestMatchers("/", "/login", "/signup", "/verify-email").permitAll()

						// E2 좌석·예약 (US-03~06), E3 QR 조회 (US-07): 학생
						.requestMatchers("/api/seats/**", "/api/reservations/**").hasRole("STUDENT")
						.requestMatchers("/seats/**", "/reservations/**").hasRole("STUDENT")

						// E3 체크인 (US-08): 도서관 단말 계정
						.requestMatchers("/api/check-ins/**", "/kiosk/**").hasRole("KIOSK")

						// E4 관리자 (US-11~15)
						.requestMatchers("/api/admin/**", "/admin/**").hasRole("ADMIN")

						// 그 외(로그아웃 등)는 로그인만 하면 허용
						.anyRequest().authenticated());
		return http.build();
	}

	/**
	 * 개발용 H2 콘솔 (http://localhost:8080/h2-console). dev 프로필에서만 켜진다.
	 * JDBC URL: jdbc:h2:mem:lsrs / User: sa / Password: (빈칸)
	 */
	@Bean
	@Order(1)
	@ConditionalOnProperty(name = "spring.h2.console.enabled", havingValue = "true")
	public SecurityFilterChain h2ConsoleFilterChain(HttpSecurity http) throws Exception {
		http
				.securityMatcher(PathRequest.toH2Console())
				.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
				.csrf(AbstractHttpConfigurer::disable)
				.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
		return http.build();
	}

	/** 미인증: API는 401 JSON, 화면은 [임시] 브라우저 Basic 로그인 창 */
	private void handleUnauthenticated(HttpServletRequest request, HttpServletResponse response,
									   AuthenticationException e) throws IOException, ServletException {
		if (isApiRequest(request)) {
			writeError(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다.");
			return;
		}
		pageEntryPoint.commence(request, response, e);
	}

	private AccessDeniedHandler accessDeniedHandler() {
		return (request, response, e) ->
				writeError(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다.");
	}

	private static boolean isApiRequest(HttpServletRequest request) {
		return request.getRequestURI().startsWith(request.getContextPath() + "/api/");
	}

	private void writeError(HttpServletResponse response, HttpStatus status, String code, String message)
			throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(code, message));
	}
}
