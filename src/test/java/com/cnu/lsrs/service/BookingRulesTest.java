package com.cnu.lsrs.service;

import com.cnu.lsrs.domain.ReservationPolicy;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 예약 가능 시간대 정책 (FR-A-20, US-03·US-04) */
class BookingRulesTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 19, 9, 0);
	private static final LocalDate TODAY = NOW.toLocalDate();

	private final BookingRules rules = new BookingRules();
	private final ReservationPolicy normal = ReservationPolicy.normal(7, 4);
	private final ReservationPolicy exam = ReservationPolicy.exam(TODAY, TODAY.plusDays(7), 1, 3);

	private String violation(LocalDate date, String start, String end, ReservationPolicy policy) {
		return rules.check(date, LocalTime.parse(start), LocalTime.parse(end), NOW, policy)
				.map(BookingRules.Violation::code).orElse(null);
	}

	@Test
	void 평상시_정책_안의_시간대는_통과한다() {
		assertThat(violation(TODAY, "10:00", "14:00", normal)).isNull();       // 최대 4시간 경계
		assertThat(violation(TODAY.plusDays(7), "10:00", "11:00", normal)).isNull(); // +7일 경계
	}

	@Test
	void 최대_이용_시간을_넘으면_거부된다() {
		assertThat(violation(TODAY, "10:00", "14:30", normal)).isEqualTo("EXCEEDS_MAX_HOURS");
		assertThat(violation(TODAY, "10:00", "13:30", exam)).isEqualTo("EXCEEDS_MAX_HOURS"); // 시험기간 3시간
		assertThat(violation(TODAY, "10:00", "13:00", exam)).isNull();
	}

	@Test
	void 예약_가능_날짜_범위를_벗어나면_거부된다() {
		assertThat(violation(TODAY.plusDays(8), "10:00", "11:00", normal)).isEqualTo("OUT_OF_BOOKING_RANGE");
		assertThat(violation(TODAY.minusDays(1), "10:00", "11:00", normal)).isEqualTo("OUT_OF_BOOKING_RANGE");
		// 시험기간: 당일·다음 날까지만
		assertThat(violation(TODAY.plusDays(1), "10:00", "11:00", exam)).isNull();
		assertThat(violation(TODAY.plusDays(2), "10:00", "11:00", exam)).isEqualTo("OUT_OF_BOOKING_RANGE");
	}

	@Test
	void 지난_시간대와_시작이_종료보다_늦은_요청은_거부된다() {
		assertThat(violation(TODAY, "08:30", "10:00", normal)).isEqualTo("PAST_TIME");
		assertThat(violation(TODAY, "09:00", "10:00", normal)).isNull(); // 지금 시작은 허용
		assertThat(violation(TODAY, "12:00", "12:00", normal)).isEqualTo("INVALID_TIME_RANGE");
		assertThat(violation(TODAY, "12:00", "11:00", normal)).isEqualTo("INVALID_TIME_RANGE");
	}
}
