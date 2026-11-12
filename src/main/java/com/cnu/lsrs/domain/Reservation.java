package com.cnu.lsrs.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 좌석 예약.
 * 상태 변경은 아래 메서드로만 하며, 허용되지 않은 전이는 IllegalStateException을 던진다
 * (설계서 Data Integrity Rules: RESERVED → CHECKED_IN | CANCELLED | AUTO_CANCELLED, CHECKED_IN → CANCELLED(관리자)).
 * 시간 조건(15분 이내 체크인 등)은 서비스 계층에서 검사한다.
 */
@Entity
@Table(name = "reservation",
		indexes = @Index(name = "idx_reservation_seat_start", columnList = "seat_id, start_at"))
public class Reservation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "reservation_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "seat_id", nullable = false)
	private Seat seat;

	@Column(name = "start_at", nullable = false)
	private LocalDateTime startAt;

	@Column(name = "end_at", nullable = false)
	private LocalDateTime endAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private ReservationStatus status;

	/** 체크인 QR에 담기는 추측 불가능한 토큰 (FR-C-01, US-07) */
	@Column(name = "qr_token", nullable = false, unique = true, length = 64)
	private String qrToken;

	@Column(name = "checked_in_at")
	private LocalDateTime checkedInAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "cancel_reason", length = 30)
	private CancelReason cancelReason;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	protected Reservation() {
		// JPA 전용
	}

	private Reservation(User user, Seat seat, LocalDateTime startAt, LocalDateTime endAt,
						String qrToken, LocalDateTime createdAt) {
		this.user = user;
		this.seat = seat;
		this.startAt = startAt;
		this.endAt = endAt;
		this.qrToken = qrToken;
		this.createdAt = createdAt;
		this.status = ReservationStatus.RESERVED; // FR-R-13
	}

	/**
	 * 예약 생성. 정책·중복·잠금 검사는 ReservationService에서 끝낸 뒤 호출한다.
	 * 여기서는 데이터 자체가 성립하는지만 확인한다.
	 */
	public static Reservation create(User user, Seat seat, LocalDateTime startAt, LocalDateTime endAt,
									 String qrToken, LocalDateTime createdAt) {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(seat, "seat");
		Objects.requireNonNull(startAt, "startAt");
		Objects.requireNonNull(endAt, "endAt");
		Objects.requireNonNull(qrToken, "qrToken");
		Objects.requireNonNull(createdAt, "createdAt");
		if (!startAt.isBefore(endAt)) {
			throw new IllegalArgumentException("시작 시각은 종료 시각보다 앞서야 합니다.");
		}
		return new Reservation(user, seat, startAt, endAt, qrToken, createdAt);
	}

	/** QR 체크인 (FR-C-03). 15분 이내인지는 CheckInService가 먼저 확인한다. */
	public void checkIn(LocalDateTime now) {
		requireStatus(ReservationStatus.RESERVED, "체크인");
		this.status = ReservationStatus.CHECKED_IN;
		this.checkedInAt = now;
	}

	/** 학생 직접 취소 (FR-X-01~03). 체크인 전 예약만 가능하고, 노쇼로 기록하지 않는다. */
	public void cancelByStudent(CancelReason reason) {
		requireReason(reason, false);
		requireStatus(ReservationStatus.RESERVED, "학생 취소");
		this.status = ReservationStatus.CANCELLED;
		this.cancelReason = reason;
	}

	/** 관리자 강제 취소 (FR-X-20, FR-X-21). RESERVED·CHECKED_IN 모두 가능하다. */
	public void cancelByAdmin(CancelReason reason) {
		requireReason(reason, true);
		if (!status.isActive()) {
			throw new IllegalStateException("이미 종료된 예약은 강제 취소할 수 없습니다. 현재 상태: " + status);
		}
		this.status = ReservationStatus.CANCELLED;
		this.cancelReason = reason;
	}

	/** 15분 내 미체크인 자동 취소 = 노쇼 (FR-X-10) */
	public void autoCancel() {
		requireStatus(ReservationStatus.RESERVED, "자동 취소");
		this.status = ReservationStatus.AUTO_CANCELLED;
	}

	public boolean isOwnedBy(Long userId) {
		return user.getId() != null && user.getId().equals(userId);
	}

	/** [startAt, endAt) 구간이 [otherStart, otherEnd)와 겹치는지. 끝 시각과 다음 시작 시각이 같으면 겹치지 않는다. */
	public boolean overlaps(LocalDateTime otherStart, LocalDateTime otherEnd) {
		return startAt.isBefore(otherEnd) && otherStart.isBefore(endAt);
	}

	private void requireStatus(ReservationStatus expected, String action) {
		if (status != expected) {
			throw new IllegalStateException(action + "는 " + expected + " 상태에서만 가능합니다. 현재 상태: " + status);
		}
	}

	private static void requireReason(CancelReason reason, boolean adminReason) {
		Objects.requireNonNull(reason, "cancelReason");
		if (reason.isAdminReason() != adminReason) {
			throw new IllegalArgumentException((adminReason ? "관리자" : "학생") + " 취소에 사용할 수 없는 사유입니다: " + reason);
		}
	}

	public Long getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public Seat getSeat() {
		return seat;
	}

	public LocalDateTime getStartAt() {
		return startAt;
	}

	public LocalDateTime getEndAt() {
		return endAt;
	}

	public ReservationStatus getStatus() {
		return status;
	}

	public String getQrToken() {
		return qrToken;
	}

	public LocalDateTime getCheckedInAt() {
		return checkedInAt;
	}

	public CancelReason getCancelReason() {
		return cancelReason;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
