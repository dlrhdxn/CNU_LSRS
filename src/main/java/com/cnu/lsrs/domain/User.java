package com.cnu.lsrs.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 사용자 (학생·관리자·체크인 단말).
 * student_no는 학생의 학번이자, 관리자·단말 계정의 로그인 ID로 쓴다 (FR-U-10).
 */
@Entity
@Table(name = "users") // USER는 일부 DB 예약어라 users 사용
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "user_id")
	private Long id;

	@Column(name = "student_no", nullable = false, unique = true, length = 30)
	private String studentNo;

	@Column(name = "email", nullable = false, unique = true, length = 100)
	private String email;

	/** BCrypt 해시 (NFR 4.5). 평문 비밀번호는 저장하지 않는다. */
	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 20)
	private Role role;

	@Enumerated(EnumType.STRING)
	@Column(name = "account_status", nullable = false, length = 20)
	private AccountStatus accountStatus;

	/** 이메일 인증용 6자리 코드 (FR-U-02). 인증 완료 후 null */
	@Column(name = "verification_code", length = 10)
	private String verificationCode;

	@Column(name = "no_show_count", nullable = false)
	private int noShowCount;

	/** 이용 제한 해제 시각. null이거나 지난 시각이면 제한 없음 (FR-R-12, FR-X-12) */
	@Column(name = "restriction_until")
	private LocalDateTime restrictionUntil;

	protected User() {
		// JPA 전용
	}

	private User(String studentNo, String email, String passwordHash, Role role,
				 AccountStatus accountStatus, String verificationCode) {
		this.studentNo = studentNo;
		this.email = email;
		this.passwordHash = passwordHash;
		this.role = role;
		this.accountStatus = accountStatus;
		this.verificationCode = verificationCode;
		this.noShowCount = 0;
	}

	/** 회원가입한 학생: 이메일 인증 전까지 PENDING (FR-U-01, FR-U-03) */
	public static User createPendingStudent(String studentNo, String email, String passwordHash,
											String verificationCode) {
		return new User(studentNo, email, passwordHash, Role.STUDENT, AccountStatus.PENDING, verificationCode);
	}

	/** 운영자가 미리 만드는 계정(관리자·체크인 단말, 초기 데이터용 학생): 바로 ACTIVE */
	public static User createActive(String loginId, String email, String passwordHash, Role role) {
		return new User(loginId, email, passwordHash, role, AccountStatus.ACTIVE, null);
	}

	/** 이메일 인증 완료 → ACTIVE 전환 (FR-U-04) */
	public void activate() {
		this.accountStatus = AccountStatus.ACTIVE;
		this.verificationCode = null;
	}

	public boolean isActive() {
		return accountStatus == AccountStatus.ACTIVE;
	}

	/** now 시점에 이용 제한 중인지 (FR-R-12) */
	public boolean isRestricted(LocalDateTime now) {
		return restrictionUntil != null && now.isBefore(restrictionUntil);
	}

	public Long getId() {
		return id;
	}

	public String getStudentNo() {
		return studentNo;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public Role getRole() {
		return role;
	}

	public AccountStatus getAccountStatus() {
		return accountStatus;
	}

	public String getVerificationCode() {
		return verificationCode;
	}

	public int getNoShowCount() {
		return noShowCount;
	}

	public LocalDateTime getRestrictionUntil() {
		return restrictionUntil;
	}
}
