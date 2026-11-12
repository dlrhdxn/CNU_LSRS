package com.cnu.lsrs.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationTest {

	private static final LocalDateTime START = LocalDateTime.of(2026, 11, 19, 10, 0);
	private static final LocalDateTime END = START.plusHours(2);

	private final User user = User.createActive("20230001", "s1@lsrs.test", "hash", Role.STUDENT);
	private final Seat seat = Seat.create("A-01", "1층");

	private Reservation newReservation() {
		return Reservation.create(user, seat, START, END, "token", START.minusDays(1));
	}

	@Test
	void 생성하면_RESERVED_상태다() {
		assertThat(newReservation().getStatus()).isEqualTo(ReservationStatus.RESERVED);
	}

	@Test
	void 시작이_종료보다_같거나_늦으면_생성할_수_없다() {
		assertThatThrownBy(() -> Reservation.create(user, seat, START, START, "t", START))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Reservation.create(user, seat, END, START, "t", START))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 체크인하면_CHECKED_IN과_체크인_시각이_기록된다() {
		Reservation reservation = newReservation();
		reservation.checkIn(START.plusMinutes(3));

		assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CHECKED_IN);
		assertThat(reservation.getCheckedInAt()).isEqualTo(START.plusMinutes(3));
	}

	@Test
	void 취소된_예약은_체크인할_수_없다() {
		Reservation reservation = newReservation();
		reservation.cancelByStudent(CancelReason.SCHEDULE_CHANGE);

		assertThatThrownBy(() -> reservation.checkIn(START))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 학생은_체크인_전_예약만_취소할_수_있다() {
		Reservation reservation = newReservation();
		reservation.checkIn(START);

		assertThatThrownBy(() -> reservation.cancelByStudent(CancelReason.SCHEDULE_CHANGE))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 학생_취소는_사유를_남기고_CANCELLED가_된다() {
		Reservation reservation = newReservation();
		reservation.cancelByStudent(CancelReason.TIME_CHANGE);

		assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
		assertThat(reservation.getCancelReason()).isEqualTo(CancelReason.TIME_CHANGE);
	}

	@Test
	void 학생_취소에_관리자_사유는_쓸_수_없고_반대도_마찬가지다() {
		assertThatThrownBy(() -> newReservation().cancelByStudent(CancelReason.RULE_VIOLATION))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> newReservation().cancelByAdmin(CancelReason.SCHEDULE_CHANGE))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 관리자는_체크인한_예약도_강제_취소할_수_있다() {
		Reservation reservation = newReservation();
		reservation.checkIn(START);
		reservation.cancelByAdmin(CancelReason.FACILITY_INSPECTION);

		assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
	}

	@Test
	void 자동_취소는_RESERVED에서만_가능하다() {
		Reservation reservation = newReservation();
		reservation.autoCancel();
		assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.AUTO_CANCELLED);

		Reservation checkedIn = newReservation();
		checkedIn.checkIn(START);
		assertThatThrownBy(checkedIn::autoCancel).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 시간_겹침_판정은_끝과_시작이_맞닿으면_겹치지_않는다() {
		Reservation reservation = newReservation(); // 10:00 ~ 12:00

		assertThat(reservation.overlaps(START.minusHours(2), START)).isFalse();      // 08:00~10:00
		assertThat(reservation.overlaps(END, END.plusHours(1))).isFalse();            // 12:00~13:00
		assertThat(reservation.overlaps(START.plusMinutes(30), END)).isTrue();        // 10:30~12:00
		assertThat(reservation.overlaps(START.minusHours(1), END.plusHours(1))).isTrue(); // 감싸는 구간
		assertThat(reservation.overlaps(START.minusMinutes(1), START.plusMinutes(1))).isTrue();
	}

	@Test
	void 활성_상태는_RESERVED와_CHECKED_IN뿐이다() {
		assertThat(ReservationStatus.RESERVED.isActive()).isTrue();
		assertThat(ReservationStatus.CHECKED_IN.isActive()).isTrue();
		assertThat(ReservationStatus.CANCELLED.isActive()).isFalse();
		assertThat(ReservationStatus.AUTO_CANCELLED.isActive()).isFalse();
	}
}
