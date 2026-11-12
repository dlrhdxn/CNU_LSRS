package com.cnu.lsrs.repository;

import com.cnu.lsrs.domain.NoShowRecord;
import org.springframework.data.jpa.repository.JpaRepository;

/** Sprint 2 (US-09, US-12, US-15)에서 사용 */
public interface NoShowRecordRepository extends JpaRepository<NoShowRecord, Long> {
}
