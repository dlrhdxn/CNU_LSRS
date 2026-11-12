package com.cnu.lsrs.security;

import com.cnu.lsrs.domain.Role;
import com.cnu.lsrs.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 로그인한 사용자 정보 (Spring Security principal).
 * 컨트롤러에서 현재 사용자는 이렇게 받는다:
 * <pre>
 *   public ... reserve(@AuthenticationPrincipal LoginUser loginUser, ...) {
 *       Long userId = loginUser.getUserId();
 * </pre>
 * JWT 필터(A 담당)도 토큰을 검증한 뒤 이 클래스로 principal을 만들어야 다른 기능이 그대로 동작한다.
 */
public class LoginUser implements UserDetails {

	private final Long userId;
	private final String loginId;
	private final String passwordHash;
	private final Role role;
	private final boolean active;

	private LoginUser(Long userId, String loginId, String passwordHash, Role role, boolean active) {
		this.userId = userId;
		this.loginId = loginId;
		this.passwordHash = passwordHash;
		this.role = role;
		this.active = active;
	}

	/** DB의 User로부터 생성 (아이디·비밀번호 로그인 시) */
	public static LoginUser from(User user) {
		return new LoginUser(user.getId(), user.getStudentNo(), user.getPasswordHash(), user.getRole(), user.isActive());
	}

	/** 검증된 JWT 내용으로 생성 (비밀번호 불필요) */
	public static LoginUser of(Long userId, String loginId, Role role) {
		return new LoginUser(userId, loginId, null, role, true);
	}

	public Long getUserId() {
		return userId;
	}

	public String getLoginId() {
		return loginId;
	}

	public Role getRole() {
		return role;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return loginId;
	}

	/** 이메일 인증 전(PENDING) 계정은 로그인할 수 없다 (FR-U-03) */
	@Override
	public boolean isEnabled() {
		return active;
	}
}
