package com.cnu.lsrs.service;

import com.cnu.lsrs.common.exception.BusinessException;
import com.cnu.lsrs.domain.ReservationPolicy;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

/**
 * 예약 정책으로 "이 시간대를 예약할 수 있는가"를 판단한다 (FR-A-20, US-03, US-04).
 * 좌석 조회(US-03)는 위반 내용을 화면에 안내만 하고, 예약(US-04)은 위반이면 거부한다.
 * 두 곳이 같은 규칙을 쓰도록 한 군데에 모았다.
 *
 * 규칙 (정책 값은 ReservationPolicy에서 읽는다, NFR 4.1):
 *  1) 시작 < 종료
 *  2) 이미 지난 시간대는 불가
 *  3) 날짜는 오늘 ~ 오늘 + bookingDays (평상시 +7일, 시험기간 당일·다음 날)
 *  4) 이용 시간은 maxHours 이하 (평상시 4시간, 시험기간 3시간)
 */
@Component
public class BookingRules {

	public record Violation(String code, String message) {
		public BusinessException toException() {
			return BusinessException.badRequest(code, message);
		}
	}

	public Optional<Violation> check(LocalDate date, LocalTime startTime, LocalTime endTime,
									 LocalDateTime now, ReservationPolicy policy) {
		if (!startTime.isBefore(endTime)) {
			return violation("INVALID_TIME_RANGE", "종료 시각은 시작 시각보다 늦어야 합니다.");
		}
		LocalDate today = now.toLocalDate();
		LocalDate lastDate = today.plusDays(policy.getBookingDays());
		if (date.isBefore(today) || date.isAfter(lastDate)) {
			return violation("OUT_OF_BOOKING_RANGE",
					"현재 정책으로는 " + today + " ~ " + lastDate + " 사이 날짜만 예약할 수 있습니다.");
		}
		if (LocalDateTime.of(date, startTime).isBefore(now)) {
			return violation("PAST_TIME", "이미 지난 시간대는 예약할 수 없습니다.");
		}
		Duration duration = Duration.between(startTime, endTime);
		if (duration.compareTo(Duration.ofHours(policy.getMaxHours())) > 0) {
			return violation("EXCEEDS_MAX_HOURS",
					"현재 정책으로는 한 번에 최대 " + policy.getMaxHours() + "시간까지 예약할 수 있습니다.");
		}
		return Optional.empty();
	}

	private static Optional<Violation> violation(String code, String message) {
		return Optional.of(new Violation(code, message));
	}
}
