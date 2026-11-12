package com.cnu.lsrs.config;

import com.cnu.lsrs.domain.Reservation;
import com.cnu.lsrs.domain.ReservationPolicy;
import com.cnu.lsrs.domain.Role;
import com.cnu.lsrs.domain.Seat;
import com.cnu.lsrs.domain.User;
import com.cnu.lsrs.repository.ReservationPolicyRepository;
import com.cnu.lsrs.repository.ReservationRepository;
import com.cnu.lsrs.repository.SeatRepository;
import com.cnu.lsrs.repository.UserRepository;
import com.cnu.lsrs.service.QrTokenGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 개발용 초기 데이터 (lsrs.seed.enabled=true일 때, 기본은 dev 프로필).
 * 테스트 계정 비밀번호는 모두 Test1234! 이다. 계정 목록은 README 참고.
 * 이미 사용자가 있으면 아무것도 하지 않는다.
 */
@Component
@ConditionalOnProperty(name = "lsrs.seed.enabled", havingValue = "true")
public class DataInitializer implements ApplicationRunner {

	public static final String TEST_PASSWORD = "Test1234!";

	private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

	private final UserRepository userRepository;
	private final SeatRepository seatRepository;
	private final ReservationRepository reservationRepository;
	private final ReservationPolicyRepository policyRepository;
	private final PasswordEncoder passwordEncoder;
	private final QrTokenGenerator qrTokenGenerator;
	private final LsrsProperties properties;
	private final Clock clock;

	public DataInitializer(UserRepository userRepository, SeatRepository seatRepository,
						   ReservationRepository reservationRepository, ReservationPolicyRepository policyRepository,
						   PasswordEncoder passwordEncoder, QrTokenGenerator qrTokenGenerator,
						   LsrsProperties properties, Clock clock) {
		this.userRepository = userRepository;
		this.seatRepository = seatRepository;
		this.reservationRepository = reservationRepository;
		this.policyRepository = policyRepository;
		this.passwordEncoder = passwordEncoder;
		this.qrTokenGenerator = qrTokenGenerator;
		this.properties = properties;
		this.clock = clock;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (userRepository.count() > 0) {
			log.info("[seed] 기존 데이터가 있어 초기 데이터를 건너뜁니다.");
			return;
		}

		// 정책: 평상시 기본값 (FR-A-20)
		LsrsProperties.PeriodDefaults normal = properties.policy().normal();
		policyRepository.save(ReservationPolicy.normal(normal.bookingDays(), normal.maxHours()));

		// 계정
		String hash = passwordEncoder.encode(TEST_PASSWORD);
		User student1 = userRepository.save(User.createActive("20230001", "student1@lsrs.test", hash, Role.STUDENT));
		User student2 = userRepository.save(User.createActive("20230002", "student2@lsrs.test", hash, Role.STUDENT));
		userRepository.save(User.createPendingStudent("20230003", "pending@lsrs.test", hash, "123456"));
		userRepository.save(User.createActive("admin", "admin@lsrs.test", hash, Role.ADMIN));
		userRepository.save(User.createActive("kiosk1", "kiosk1@lsrs.test", hash, Role.KIOSK));

		// 좌석: 1층 A-01~A-12, 2층 B-01~B-08 (B-08은 사용 불가)
		List<Seat> seats = new ArrayList<>();
		for (int i = 1; i <= 12; i++) {
			seats.add(Seat.create(String.format("A-%02d", i), "1층 열람실 A"));
		}
		for (int i = 1; i <= 8; i++) {
			seats.add(Seat.create(String.format("B-%02d", i), "2층 열람실 B"));
		}
		seats.get(seats.size() - 1).markUnavailable();
		seatRepository.saveAll(seats);

		// 예약: 기능 담당자가 바로 확인해 볼 수 있는 상태들
		LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
		// 1) 5분 전 시작, 아직 체크인 안 함 → QR 체크인 가능 (US-08)
		reservationRepository.save(Reservation.create(student1, seats.get(0),
				now.minusMinutes(5), now.plusHours(2), qrTokenGenerator.generate(), now.minusHours(1)));
		// 2) 2시간 뒤 시작 → 취소 가능 (US-05), 좌석 '예약됨' 표시 (US-03)
		reservationRepository.save(Reservation.create(student2, seats.get(1),
				now.plusHours(2), now.plusHours(4), qrTokenGenerator.generate(), now.minusHours(1)));
		// 3) 30분 전 시작, 체크인 완료 → 좌석 '사용 중' 표시 (US-03)
		Reservation checkedIn = Reservation.create(student2, seats.get(2),
				now.minusMinutes(30), now.plusHours(1), qrTokenGenerator.generate(), now.minusHours(2));
		checkedIn.checkIn(now.minusMinutes(25));
		reservationRepository.save(checkedIn);

		log.info("[seed] 초기 데이터 생성 완료: 좌석 {}개, 계정 5개(비밀번호 {}), 예약 3건",
				seats.size(), TEST_PASSWORD);
	}
}
