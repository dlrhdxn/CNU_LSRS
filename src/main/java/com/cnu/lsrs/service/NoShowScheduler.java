package com.cnu.lsrs.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 노쇼 스케줄러 (US-09, NFR 4.4). lsrs.policy.no-show-scan-interval(기본 10분)마다 실행한다.
 * 이전 실행이 끝난 뒤부터 주기를 세므로(fixedDelay) 두 실행이 겹치지 않는다.
 * 첫 실행도 한 주기 뒤에 한다 (앱 시작 직후 초기 데이터·테스트 데이터를 건드리지 않게).
 */
@Component
public class NoShowScheduler {

	private static final Logger log = LoggerFactory.getLogger(NoShowScheduler.class);

	private final NoShowService noShowService;

	public NoShowScheduler(NoShowService noShowService) {
		this.noShowService = noShowService;
	}

	@Scheduled(initialDelayString = "${lsrs.policy.no-show-scan-interval}",
			fixedDelayString = "${lsrs.policy.no-show-scan-interval}")
	public void run() {
		int count = noShowService.processNoShows();
		if (count > 0) {
			log.info("노쇼 자동 취소 {}건", count);
		}
	}
}
