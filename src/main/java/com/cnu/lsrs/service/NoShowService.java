package com.cnu.lsrs.service;

import com.cnu.lsrs.config.LsrsProperties;
import com.cnu.lsrs.domain.NoShowRecord;
import com.cnu.lsrs.domain.NoShowSource;
import com.cnu.lsrs.domain.Reservation;
import com.cnu.lsrs.domain.User;
import com.cnu.lsrs.repository.NoShowRecordRepository;
import com.cnu.lsrs.repository.ReservationRepository;
import com.cnu.lsrs.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * US-09 미체크인 자동 취소·노쇼 기록, US-10 반복 노쇼 이용 제한.
 * 예약 시작 후 15분이 지나도 RESERVED인 예약을 AUTO_CANCELLED로 바꾸고 (FR-X-10),
 * 노쇼 이력을 남기고 (FR-X-11), 노쇼 횟수를 올린다. 3회가 되면 3일 제한이 걸린다 (FR-X-12, User.recordNoShow).
 */
@Service
public class NoShowService {

	private final ReservationRepository reservationRepository;
	private final UserRepository userRepository;
	private final NoShowRecordRepository noShowRecordRepository;
	private final LsrsProperties properties;
	private final Clock clock;

	public NoShowService(ReservationRepository reservationRepository, UserRepository userRepository,
						 NoShowRecordRepository noShowRecordRepository, LsrsProperties properties, Clock clock) {
		this.reservationRepository = reservationRepository;
		this.userRepository = userRepository;
		this.noShowRecordRepository = noShowRecordRepository;
		this.properties = properties;
		this.clock = clock;
	}

	/** @return 이번 실행에서 노쇼 처리한 예약 수 */
	@Transactional
	public int processNoShows() {
		LsrsProperties.Policy policy = properties.policy();
		LocalDateTime now = LocalDateTime.now(clock);
		List<Reservation> candidates = reservationRepository.findNoShowCandidatesForUpdate(
				now.minus(policy.checkInWindow()));

		for (Reservation reservation : candidates) {
			// 노쇼 횟수를 바꾸므로 사용자 행도 잠근다 (관리자 패널티와 동시에 일어나도 횟수가 유실되지 않게)
			User user = userRepository.findByIdForUpdate(reservation.getUser().getId()).orElseThrow();
			reservation.autoCancel();
			user.recordNoShow(policy.noShowThreshold(), policy.restrictionPeriod(), now);
			noShowRecordRepository.save(NoShowRecord.of(reservation, NoShowSource.AUTO, now));
		}
		return candidates.size();
	}
}
