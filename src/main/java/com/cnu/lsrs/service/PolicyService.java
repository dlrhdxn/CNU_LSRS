package com.cnu.lsrs.service;

import com.cnu.lsrs.domain.PeriodType;
import com.cnu.lsrs.domain.ReservationPolicy;
import com.cnu.lsrs.repository.ReservationPolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 날짜별로 적용할 예약 정책을 결정한다 (FR-A-20, FR-R-03).
 * 시험기간(EXAM)이 date를 포함하면 그 정책을, 아니면 평상시(NORMAL) 정책을 쓴다.
 * 좌석 조회 화면의 정책 표시(US-03)와 예약 검사(US-04)가 이 결과를 사용한다.
 */
@Service
@Transactional(readOnly = true)
public class PolicyService {

	private final ReservationPolicyRepository policyRepository;

	public PolicyService(ReservationPolicyRepository policyRepository) {
		this.policyRepository = policyRepository;
	}

	public ReservationPolicy getActivePolicy(LocalDate date) {
		return policyRepository
				.findFirstByPeriodTypeAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
						PeriodType.EXAM, date, date)
				.orElseGet(() -> policyRepository.findFirstByPeriodTypeOrderByIdAsc(PeriodType.NORMAL)
						.orElseThrow(() -> new IllegalStateException("평상시(NORMAL) 예약 정책이 DB에 없습니다.")));
	}
}
