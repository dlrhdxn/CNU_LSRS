package com.cnu.lsrs.service;

import com.cnu.lsrs.common.exception.BusinessException;
import com.cnu.lsrs.config.LsrsProperties;
import com.cnu.lsrs.domain.Role;
import com.cnu.lsrs.domain.User;
import com.cnu.lsrs.repository.UserRepository;
import com.cnu.lsrs.security.JwtProvider;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.jsonwebtoken.Claims;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class AuthService {

	private static final Pattern PASSWORD_PATTERN =
			Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$");
	private static final SecureRandom RANDOM = new SecureRandom();

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final MailService mailService;
	private final JwtProvider jwtProvider;
	private final LsrsProperties properties;
	private final Clock clock;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
					   MailService mailService, JwtProvider jwtProvider, LsrsProperties properties, Clock clock) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.mailService = mailService;
		this.jwtProvider = jwtProvider;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional
	public RegisterResult register(String studentNo, String email, String password) {
		studentNo = requireText(studentNo).trim();
		email = requireText(email).trim().toLowerCase(Locale.ROOT);
		password = requireText(password);

		if (!email.endsWith("@o.cnu.ac.kr") && !email.endsWith("@cnu.ac.kr")) {
			throw BusinessException.badRequest("INVALID_EMAIL_DOMAIN",
					"이메일은 @o.cnu.ac.kr 또는 @cnu.ac.kr 주소를 사용해야 합니다.");
		}
		if (!PASSWORD_PATTERN.matcher(password).matches()) {
			throw BusinessException.badRequest("WEAK_PASSWORD",
					"비밀번호는 8자 이상이며 영문, 숫자, 특수문자를 모두 포함해야 합니다.");
		}
		if (userRepository.findByStudentNo(studentNo).isPresent()) {
			throw BusinessException.conflict("DUPLICATE_STUDENT_NO", "이미 등록된 학번 또는 ID입니다.");
		}
		if (userRepository.findByEmail(email).isPresent()) {
			throw BusinessException.conflict("DUPLICATE_EMAIL", "이미 등록된 이메일입니다.");
		}

		String code = String.format("%06d", RANDOM.nextInt(1_000_000));
		User user = User.createPendingStudent(studentNo, email, passwordEncoder.encode(password), code);
		userRepository.save(user);
		mailService.sendVerificationCode(email, code);
		return new RegisterResult(user.getId(), user.getStudentNo(), user.getAccountStatus().name());
	}

	@Transactional
	public VerifyResult verifyEmail(String studentNo, String email, String code) {
		User user;
		if (studentNo != null && !studentNo.isBlank()) {
			user = userRepository.findByStudentNo(studentNo.trim()).orElseThrow(
					() -> BusinessException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
		} else if (email != null && !email.isBlank()) {
			user = userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT)).orElseThrow(
					() -> BusinessException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
		} else {
			throw BusinessException.badRequest("INVALID_INPUT", "학번 또는 이메일을 입력해야 합니다.");
		}
		if (user.isActive()) {
			throw BusinessException.conflict("ALREADY_VERIFIED", "이미 이메일 인증이 완료된 계정입니다.");
		}
		if (code == null || code.isBlank() || !code.trim().equals(user.getVerificationCode())) {
			throw BusinessException.badRequest("INVALID_VERIFICATION_CODE", "인증 코드가 올바르지 않습니다.");
		}
		user.activate();
		return new VerifyResult(user.getId(), user.getStudentNo(), user.getAccountStatus().name());
	}

	@Transactional(readOnly = true)
	public LoginResult login(String loginId, String password) {
		loginId = requireText(loginId).trim();
		password = requireText(password);
		User user = userRepository.findByStudentNo(loginId).orElseThrow(
				() -> BusinessException.unauthorized("INVALID_CREDENTIALS", "아이디 또는 비밀번호가 올바르지 않습니다."));
		if (!passwordEncoder.matches(password, user.getPasswordHash())) {
			throw BusinessException.unauthorized("INVALID_CREDENTIALS", "아이디 또는 비밀번호가 올바르지 않습니다.");
		}
		if (!user.isActive()) {
			throw BusinessException.unauthorized("ACCOUNT_PENDING", "이메일 인증이 완료되지 않은 계정입니다.");
		}
		String accessToken = jwtProvider.createAccessToken(user.getId(), user.getStudentNo(), user.getRole());
		String refreshToken = jwtProvider.createRefreshToken(user.getId(), user.getStudentNo(), user.getRole());
		Instant now = clock.instant();
		return new LoginResult(user.getId(), user.getStudentNo(), user.getRole(),
				accessToken, refreshToken,
				now.plus(properties.jwt().accessTokenTtl()).toString(),
				now.plus(properties.jwt().refreshTokenTtl()).toString());
	}

	public LoginResult refresh(String refreshToken) {
		Claims claims;
		try {
			claims = jwtProvider.parse(refreshToken);
		} catch (RuntimeException e) {
			throw BusinessException.unauthorized("INVALID_REFRESH_TOKEN", "Refresh 토큰이 유효하지 않습니다.");
		}
		if (!"refresh".equals(claims.get("type", String.class))) {
			throw BusinessException.unauthorized("INVALID_REFRESH_TOKEN", "Refresh 토큰이 필요합니다.");
		}
		User user = userRepository.findById(Long.valueOf(claims.getSubject())).orElseThrow(
				() -> BusinessException.unauthorized("INVALID_REFRESH_TOKEN", "Refresh 토큰이 유효하지 않습니다."));
		if (!user.isActive()) {
			throw BusinessException.unauthorized("ACCOUNT_PENDING", "이메일 인증이 완료되지 않은 계정입니다.");
		}
		String accessToken = jwtProvider.createAccessToken(user.getId(), user.getStudentNo(), user.getRole());
		String newRefreshToken = jwtProvider.createRefreshToken(user.getId(), user.getStudentNo(), user.getRole());
		Instant now = clock.instant();
		return new LoginResult(user.getId(), user.getStudentNo(), user.getRole(),
				accessToken, newRefreshToken,
				now.plus(properties.jwt().accessTokenTtl()).toString(),
				now.plus(properties.jwt().refreshTokenTtl()).toString());
	}

	private String requireText(String value) {
		if (value == null || value.isBlank()) {
			throw BusinessException.badRequest("INVALID_INPUT", "필수 입력값이 누락되었습니다.");
		}
		return value;
	}

	public record RegisterResult(Long userId, String loginId, String accountStatus) {}
	public record VerifyResult(Long userId, String loginId, String accountStatus) {}
	public record LoginResult(Long userId, String loginId, Role role,
							  @JsonIgnore String accessToken,
							  @JsonIgnore String refreshToken,
							  String accessTokenExpiresAt,
							  String refreshTokenExpiresAt) {}
}
