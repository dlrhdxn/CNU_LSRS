package com.cnu.lsrs.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

/** 노쇼 이력 (FR-X-11). 한 예약에는 최대 1건이다 (reservation_id UNIQUE). Sprint 2(US-09, US-15)에서 사용. */
@Entity
@Table(name = "no_show_record")
public class NoShowRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "record_id")
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "reservation_id", nullable = false, unique = true)
	private Reservation reservation;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(name = "source", nullable = false, length = 20)
	private NoShowSource source;

	@Column(name = "occurred_at", nullable = false)
	private LocalDateTime occurredAt;

	protected NoShowRecord() {
		// JPA 전용
	}

	private NoShowRecord(Reservation reservation, NoShowSource source, LocalDateTime occurredAt) {
		this.reservation = reservation;
		this.user = reservation.getUser();
		this.source = source;
		this.occurredAt = occurredAt;
	}

	public static NoShowRecord of(Reservation reservation, NoShowSource source, LocalDateTime occurredAt) {
		Objects.requireNonNull(reservation, "reservation");
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(occurredAt, "occurredAt");
		return new NoShowRecord(reservation, source, occurredAt);
	}

	public Long getId() {
		return id;
	}

	public Reservation getReservation() {
		return reservation;
	}

	public User getUser() {
		return user;
	}

	public NoShowSource getSource() {
		return source;
	}

	public LocalDateTime getOccurredAt() {
		return occurredAt;
	}
}
