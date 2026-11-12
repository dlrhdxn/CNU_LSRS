package com.cnu.lsrs.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 현재 시각은 반드시 이 Clock을 주입받아 LocalDateTime.now(clock)으로 구한다.
 * 테스트에서 Clock을 고정하면 "15분 경과" 같은 시간 조건을 검증할 수 있다 (NFR 4.2).
 * LocalDateTime.now()를 직접 호출하지 않는다.
 */
@Configuration
public class ClockConfig {

	public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	@Bean
	public Clock clock() {
		return Clock.system(ZONE);
	}
}
