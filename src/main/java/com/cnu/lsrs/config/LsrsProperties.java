package com.cnu.lsrs.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * application.yml의 lsrs.* 설정 (NFR 4.1: 정책 값은 코드에 하드코딩하지 않는다).
 * 값을 바꾸면 코드 수정 없이 동작이 바뀐다.
 */
@Validated
@ConfigurationProperties(prefix = "lsrs")
public record LsrsProperties(
		@Valid @NotNull Policy policy,
		@Valid @NotNull Jwt jwt,
		@Valid @NotNull Seed seed
) {

	/**
	 * @param checkInWindow     예약 시작 후 체크인 허용 시간 (FR-C-02: 15분)
	 * @param noShowThreshold   이용 제한이 걸리는 노쇼 횟수 (FR-X-12: 3회)
	 * @param restrictionPeriod 이용 제한 기간 (FR-X-12: 3일)
	 * @param noShowScanInterval 노쇼 스케줄러 실행 주기 (NFR 4.4: 10분)
	 * @param normal            평상시 정책 기본값 (FR-A-20: 오늘~+7일, 최대 4시간)
	 * @param exam              시험기간 정책 기본값 (FR-A-20: 당일·다음 날, 최대 3시간)
	 */
	public record Policy(
			@NotNull Duration checkInWindow,
			@Min(1) int noShowThreshold,
			@NotNull Duration restrictionPeriod,
			@NotNull Duration noShowScanInterval,
			@Valid @NotNull PeriodDefaults normal,
			@Valid @NotNull PeriodDefaults exam
	) {
	}

	public record PeriodDefaults(@Min(0) int bookingDays, @Min(1) int maxHours) {
	}

	/** JWT 설정 (NFR 4.5: Access 1시간, Refresh 7일). secret은 환경변수 JWT_SECRET으로 덮어쓴다. */
	public record Jwt(
			@NotBlank String secret,
			@NotNull Duration accessTokenTtl,
			@NotNull Duration refreshTokenTtl
	) {
	}

	/** 개발용 초기 데이터 생성 여부 */
	public record Seed(boolean enabled) {
	}
}
