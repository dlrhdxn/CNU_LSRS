package com.cnu.lsrs.service;

import com.cnu.lsrs.domain.PeriodType;
import com.cnu.lsrs.domain.ReservationPolicy;
import com.cnu.lsrs.repository.ReservationPolicyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@Import(PolicyService.class)
class PolicyServiceTest {

	@Autowired
	ReservationPolicyRepository policyRepository;

	@Autowired
	PolicyService policyService;

	@Test
	void 시험기간이_없으면_평상시_정책을_쓴다() {
		policyRepository.save(ReservationPolicy.normal(7, 4));

		ReservationPolicy policy = policyService.getActivePolicy(LocalDate.of(2026, 11, 19));

		assertThat(policy.getPeriodType()).isEqualTo(PeriodType.NORMAL);
		assertThat(policy.getBookingDays()).isEqualTo(7);
		assertThat(policy.getMaxHours()).isEqualTo(4);
	}

	@Test
	void 시험기간에는_시험기간_정책을_쓰고_시작일과_종료일도_포함한다() {
		policyRepository.save(ReservationPolicy.normal(7, 4));
		policyRepository.save(ReservationPolicy.exam(LocalDate.of(2026, 12, 7), LocalDate.of(2026, 12, 18), 1, 3));

		assertThat(policyService.getActivePolicy(LocalDate.of(2026, 12, 7)).getPeriodType()).isEqualTo(PeriodType.EXAM);
		assertThat(policyService.getActivePolicy(LocalDate.of(2026, 12, 18)).getMaxHours()).isEqualTo(3);
		assertThat(policyService.getActivePolicy(LocalDate.of(2026, 12, 6)).getPeriodType()).isEqualTo(PeriodType.NORMAL);
		assertThat(policyService.getActivePolicy(LocalDate.of(2026, 12, 19)).getPeriodType()).isEqualTo(PeriodType.NORMAL);
	}

	@Test
	void 평상시_정책이_DB에_없으면_예외가_난다() {
		assertThatThrownBy(() -> policyService.getActivePolicy(LocalDate.of(2026, 11, 19)))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 시험기간_종료일이_시작일보다_앞서면_만들_수_없다() {
		assertThatThrownBy(() -> ReservationPolicy.exam(LocalDate.of(2026, 12, 18), LocalDate.of(2026, 12, 7), 1, 3))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
