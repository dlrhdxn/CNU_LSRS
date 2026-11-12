package com.cnu.lsrs.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 예약 정책 (FR-A-20).
 * bookingDays: 오늘부터 며칠 뒤까지 예약 가능한지 (평상시 7 = 오늘~+7일, 시험기간 1 = 당일·다음 날)
 * maxHours: 한 번에 예약할 수 있는 최대 시간 (평상시 4, 시험기간 3)
 * NORMAL은 기간 없이 항상 적용되는 기본 정책이고, EXAM은 startDate~endDate 동안 NORMAL보다 우선한다.
 */
@Entity
@Table(name = "reservation_policy")
public class ReservationPolicy {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "policy_id")
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "period_type", nullable = false, length = 20)
	private PeriodType periodType;

	@Column(name = "start_date")
	private LocalDate startDate;

	@Column(name = "end_date")
	private LocalDate endDate;

	@Column(name = "booking_days", nullable = false)
	private int bookingDays;

	@Column(name = "max_hours", nullable = false)
	private int maxHours;

	protected ReservationPolicy() {
		// JPA 전용
	}

	private ReservationPolicy(PeriodType periodType, LocalDate startDate, LocalDate endDate,
							  int bookingDays, int maxHours) {
		if (bookingDays < 0 || maxHours <= 0) {
			throw new IllegalArgumentException("bookingDays는 0 이상, maxHours는 1 이상이어야 합니다.");
		}
		this.periodType = periodType;
		this.startDate = startDate;
		this.endDate = endDate;
		this.bookingDays = bookingDays;
		this.maxHours = maxHours;
	}

	public static ReservationPolicy normal(int bookingDays, int maxHours) {
		return new ReservationPolicy(PeriodType.NORMAL, null, null, bookingDays, maxHours);
	}

	public static ReservationPolicy exam(LocalDate startDate, LocalDate endDate, int bookingDays, int maxHours) {
		Objects.requireNonNull(startDate, "startDate");
		Objects.requireNonNull(endDate, "endDate");
		if (endDate.isBefore(startDate)) {
			throw new IllegalArgumentException("시험기간 종료일은 시작일보다 앞설 수 없습니다.");
		}
		return new ReservationPolicy(PeriodType.EXAM, startDate, endDate, bookingDays, maxHours);
	}

	/** date가 이 정책의 적용 기간에 포함되는지. NORMAL은 항상 true */
	public boolean covers(LocalDate date) {
		if (periodType == PeriodType.NORMAL) {
			return true;
		}
		return !date.isBefore(startDate) && !date.isAfter(endDate);
	}

	public Long getId() {
		return id;
	}

	public PeriodType getPeriodType() {
		return periodType;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public LocalDate getEndDate() {
		return endDate;
	}

	public int getBookingDays() {
		return bookingDays;
	}

	public int getMaxHours() {
		return maxHours;
	}
}
