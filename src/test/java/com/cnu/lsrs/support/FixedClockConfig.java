package com.cnu.lsrs.support;

import com.cnu.lsrs.config.ClockConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.LocalDateTime;

/** 테스트 시각을 2026-11-19(목) 09:00으로 고정한다 (NFR 4.2). @Import(FixedClockConfig.class)로 사용 */
@TestConfiguration
public class FixedClockConfig {

	public static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 19, 9, 0);

	@Bean
	@Primary
	public Clock fixedClock() {
		return Clock.fixed(NOW.atZone(ClockConfig.ZONE).toInstant(), ClockConfig.ZONE);
	}
}
