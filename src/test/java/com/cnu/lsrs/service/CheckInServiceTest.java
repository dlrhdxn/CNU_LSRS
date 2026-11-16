package com.cnu.lsrs.service;

import com.cnu.lsrs.config.LsrsProperties;
import com.cnu.lsrs.domain.Reservation;
import com.cnu.lsrs.domain.ReservationStatus;
import com.cnu.lsrs.domain.Role;
import com.cnu.lsrs.domain.Seat;
import com.cnu.lsrs.domain.User;
import com.cnu.lsrs.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CheckInServiceTest {

	private static final String TOKEN = "secure-test-token";
	private static final LocalDateTime START_AT = LocalDateTime.of(2026, 11, 13, 13, 0);

	private final ReservationRepository reservationRepository = mock(ReservationRepository.class);
	private final Clock clock = Clock.fixed(Instant.parse("2026-11-13T04:15:00Z"),
			ZoneId.of("Asia/Seoul"));
	private final LsrsProperties properties = new LsrsProperties(
			new LsrsProperties.Policy(Duration.ofMinutes(15), 3, Duration.ofDays(3),
					Duration.ofMinutes(10), new LsrsProperties.PeriodDefaults(7, 4),
					new LsrsProperties.PeriodDefaults(1, 3)),
			new LsrsProperties.Jwt("test-secret", Duration.ofHours(1), Duration.ofDays(7)),
			new LsrsProperties.Seed(false));

	private CheckInService checkInService;
	private Reservation reservation;

	@BeforeEach
	void setUp() {
		checkInService = new CheckInService(reservationRepository, properties, clock);
		User user = User.createActive("20230001", "student@lsrs.test", "hash", Role.STUDENT);
		Seat seat = Seat.create("A-01", "1층 열람실 A");
		reservation = Reservation.create(user, seat, START_AT, START_AT.plusHours(2), TOKEN, START_AT.minusDays(1));
		when(reservationRepository.findByQrTokenForUpdate(TOKEN)).thenReturn(Optional.of(reservation));
	}

	@Test
	void 체크인_허용_마지막_시각에도_체크인된다() {
		CheckInService.CheckInResult result = checkInService.checkIn("A-01", TOKEN);

		assertThat(result.status()).isEqualTo(ReservationStatus.CHECKED_IN);
		assertThat(result.seatCode()).isEqualTo("A-01");
		assertThat(result.checkedInAt()).isEqualTo(START_AT.plusMinutes(15));
	}

	@Test
	void 예약_시작_전에는_체크인할_수_없다() {
		CheckInService earlyService = new CheckInService(reservationRepository, properties,
				Clock.fixed(Instant.parse("2026-11-13T03:59:59Z"), ZoneId.of("Asia/Seoul")));

		assertThatThrownBy(() -> earlyService.checkIn("A-01", TOKEN))
				.hasMessageContaining("예약 시작 시각 이후");
	}

	@Test
	void 체크인_허용_시간이_지난_QR은_거부된다() {
		CheckInService lateService = new CheckInService(reservationRepository, properties,
				Clock.fixed(Instant.parse("2026-11-13T04:15:01Z"), ZoneId.of("Asia/Seoul")));

		assertThatThrownBy(() -> lateService.checkIn("A-01", TOKEN))
				.hasMessageContaining("체크인 허용 시간이 지났습니다.");
	}

	@Test
	void QR에_담긴_좌석이_예약과_다르면_체크인할_수_없다() {
		assertThatThrownBy(() -> checkInService.checkIn("A-02", TOKEN))
				.hasMessageContaining("좌석 정보가 예약과 일치하지 않습니다.");
	}

	@Test
	void 예약이_이미_체크인되었으면_다시_체크인할_수_없다() {
		reservation.checkIn(START_AT.plusMinutes(1));

		assertThatThrownBy(() -> checkInService.checkIn("A-01", TOKEN))
				.hasMessageContaining("체크인 가능한 예약 상태가 아닙니다.");
	}
}
