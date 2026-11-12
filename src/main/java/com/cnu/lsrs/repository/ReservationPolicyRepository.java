package com.cnu.lsrs.repository;

import com.cnu.lsrs.domain.PeriodType;
import com.cnu.lsrs.domain.ReservationPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface ReservationPolicyRepository extends JpaRepository<ReservationPolicy, Long> {

	Optional<ReservationPolicy> findFirstByPeriodTypeOrderByIdAsc(PeriodType periodType);

	/** date를 포함하는 정책 중 가장 최근에 시작한 것 (시험기간 조회용) */
	Optional<ReservationPolicy> findFirstByPeriodTypeAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
			PeriodType periodType, LocalDate startDateBound, LocalDate endDateBound);
}
