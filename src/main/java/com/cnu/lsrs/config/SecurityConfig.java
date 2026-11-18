package com.cnu.lsrs.config;

import com.cnu.lsrs.common.exception.ErrorResponse;
import com.cnu.lsrs.security.JwtAuthFilter;
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
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import java.io.IOException;

/**
 * URL별 접근 권한 (FR-U-12, NFR 4.5). 설계서 4장 API 목록 기준으로 Sprint 1·2 경로를 모두 미리 정해 둔다.
 *
 * JwtAuthFilter로 쿠키·Bearer JWT 인증을 적용한다.
 * HTTP Basic은 기존 개발·테스트 호환용으로만 남아 있다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final ObjectMapper objectMapper;
	private final JwtAuthFilter jwtAuthFilter;

	/** 화면 요청 미인증 시 로그인 페이지로 이동한다. */
	private final AuthenticationEntryPoint pageEntryPoint;

	public SecurityConfig(ObjectMapper objectMapper, JwtAuthFilter jwtAuthFilter) {
		this.objectMapper = objectMapper;
		this.jwtAuthFilter = jwtAuthFilter;
		this.pageEntryPoint = new LoginUrlAuthenticationEntryPoint("/login");
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
				.httpBasic(Customizer.withDefaults()) // 개발·기존 테스트 호환용
				.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint(this::handleUnauthenticated)
						.accessDeniedHandler(accessDeniedHandler()))
				.authorizeHttpRequests(auth -> auth
						// 정적 리소스·오류 페이지
						.requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
						.requestMatchers("/error").permitAll()

						// E1 회원·인증 (US-01, US-02): 비로그인 허용
						.requestMatchers(HttpMethod.POST,
								"/api/auth/register", "/api/auth/email-verification",
								"/api/auth/login", "/api/auth/refresh").permitAll()
						.requestMatchers("/", "/login", "/signup", "/verify-email").permitAll()

						// 학생과 관리자 모두 좌석·예약 기능을 사용할 수 있다.
						.requestMatchers("/api/seats/**", "/api/reservations/**")
								.hasAnyRole("STUDENT", "ADMIN")
						.requestMatchers("/seats/**", "/reservations/**")
								.hasAnyRole("STUDENT", "ADMIN")

						// E3 체크인 (US-08): 도서관 단말 계정
						.requestMatchers("/api/check-in/**").hasRole("KIOSK")

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

	/** 미인증: API는 401 JSON, 화면은 로그인 페이지로 이동 */
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
