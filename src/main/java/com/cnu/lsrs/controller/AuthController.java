package com.cnu.lsrs.controller;

import com.cnu.lsrs.common.exception.BusinessException;
import com.cnu.lsrs.service.AuthService;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	public static final String REFRESH_COOKIE = "refresh_token";

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public AuthService.RegisterResult register(@Valid @RequestBody RegisterRequest request) {
		return authService.register(request.studentNo(), request.email(), request.password());
	}

	@PostMapping("/email-verification")
	public AuthService.VerifyResult verify(@Valid @RequestBody VerifyRequest request) {
		return authService.verifyEmail(request.studentNo(), request.email(), request.code());
	}

	@PostMapping("/login")
	public AuthService.LoginResult login(@Valid @RequestBody LoginRequest request,
										 HttpServletResponse response) {
		String loginId = request.loginId();
		if (loginId == null) {
			loginId = request.studentNo();
		}
		AuthService.LoginResult result = authService.login(loginId, request.password());
		addAuthCookies(response, result.accessToken(), result.refreshToken());
		return result;
	}

	@PostMapping("/refresh")
	public AuthService.LoginResult refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
										   HttpServletRequest request, HttpServletResponse response) {
		if (refreshToken == null) {
			refreshToken = readCookie(request, REFRESH_COOKIE);
		}
		if (refreshToken == null) {
			throw BusinessException.unauthorized("INVALID_REFRESH_TOKEN", "Refresh 토큰이 필요합니다.");
		}
		AuthService.LoginResult result = authService.refresh(refreshToken);
		addAuthCookies(response, result.accessToken(), result.refreshToken());
		return result;
	}

	@PostMapping("/logout")
	public void logout(HttpServletResponse response) throws IOException {
		ResponseCookie access = cookie("access_token", "", 0);
		ResponseCookie refresh = cookie(REFRESH_COOKIE, "", 0);
		response.setHeader(HttpHeaders.SET_COOKIE, access.toString());
		response.addHeader(HttpHeaders.SET_COOKIE, refresh.toString());
		response.sendRedirect("/login");
	}

	private void addAuthCookies(HttpServletResponse response, String accessToken, String refreshToken) {
		response.setHeader(HttpHeaders.SET_COOKIE, cookie("access_token", accessToken, 60 * 60).toString());
		response.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, refreshToken, 7 * 24 * 60 * 60).toString());
	}

	private ResponseCookie cookie(String name, String value, long maxAgeSeconds) {
		return ResponseCookie.from(name, value)
				.httpOnly(true)
				.secure(false)
				.sameSite("Lax")
				.path("/")
				.maxAge(maxAgeSeconds)
				.build();
	}

	private String readCookie(HttpServletRequest request, String name) {
		if (request.getCookies() == null) {
			return null;
		}
		for (Cookie cookie : request.getCookies()) {
			if (name.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}

	public record RegisterRequest(
			@JsonAlias({"loginId", "studentNo"}) @NotBlank(message = "학번 또는 로그인 ID를 입력하세요.") String studentNo,
			@NotBlank(message = "이메일을 입력하세요.") @Email(message = "이메일 형식이 올바르지 않습니다.") String email,
			@NotBlank(message = "비밀번호를 입력하세요.") String password) {}

	public record VerifyRequest(
			String studentNo,
			@Email(message = "이메일 형식이 올바르지 않습니다.") String email,
			@JsonAlias({"verificationCode", "code"})
			@NotBlank(message = "인증 코드를 입력하세요.") String code) {}

	public record LoginRequest(
			String loginId,
			String studentNo,
			@NotBlank(message = "비밀번호를 입력하세요.") String password) {}
}
