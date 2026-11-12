package com.cnu.lsrs.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 좌석. 예약 여부는 Reservation으로 판단하고, 여기서는 좌석 자체의 사용 가능 여부만 관리한다. */
@Entity
@Table(name = "seat")
public class Seat {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "seat_id")
	private Long id;

	/** 좌석 번호 (예: A-01). QR에도 담긴다 (FR-C-01) */
	@Column(name = "seat_code", nullable = false, unique = true, length = 20)
	private String seatCode;

	@Column(name = "location", nullable = false, length = 50)
	private String location;

	@Enumerated(EnumType.STRING)
	@Column(name = "seat_status", nullable = false, length = 20)
	private SeatStatus status;

	protected Seat() {
		// JPA 전용
	}

	private Seat(String seatCode, String location, SeatStatus status) {
		this.seatCode = seatCode;
		this.location = location;
		this.status = status;
	}

	public static Seat create(String seatCode, String location) {
		return new Seat(seatCode, location, SeatStatus.AVAILABLE);
	}

	public boolean isAvailable() {
		return status == SeatStatus.AVAILABLE;
	}

	public void markUnavailable() {
		this.status = SeatStatus.UNAVAILABLE;
	}

	public void markAvailable() {
		this.status = SeatStatus.AVAILABLE;
	}

	public Long getId() {
		return id;
	}

	public String getSeatCode() {
		return seatCode;
	}

	public String getLocation() {
		return location;
	}

	public SeatStatus getStatus() {
		return status;
	}
}
