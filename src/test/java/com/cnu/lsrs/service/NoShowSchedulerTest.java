package com.cnu.lsrs.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US-09 "스케줄러는 정해진 주기로 체크인 미이행 예약을 확인한다" (NFR 4.4: 10분).
 * 실제로 10분을 기다리지 않고, Spring에 등록된 @Scheduled 작업 목록에서 주기를 확인한다.
 * (@EnableScheduling이나 @Scheduled가 빠지면 이 테스트가 실패한다)
 */
@SpringBootTest
@ActiveProfiles("test")
class NoShowSchedulerTest {

	@Autowired ScheduledTaskHolder taskHolder;

	@Test
	void 노쇼_스케줄러가_10분_주기로_등록된다() {
		// 작업 이름은 "클래스.메서드" 형식 (Spring 6.2는 실행 객체를 한 번 감싸서 이름으로 찾는다)
		String name = NoShowScheduler.class.getName() + ".run";
		FixedDelayTask task = taskHolder.getScheduledTasks().stream()
				.filter(t -> t.toString().equals(name))
				.map(ScheduledTask::getTask)
				.filter(t -> t instanceof FixedDelayTask)
				.map(t -> (FixedDelayTask) t)
				.findFirst()
				.orElseThrow(() -> new AssertionError("NoShowScheduler가 스케줄러에 등록되지 않았습니다."));

		assertThat(task.getIntervalDuration()).isEqualTo(Duration.ofMinutes(10));
		assertThat(task.getInitialDelayDuration()).isEqualTo(Duration.ofMinutes(10));
	}
}
