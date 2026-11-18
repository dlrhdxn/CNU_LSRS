package com.cnu.lsrs.repository;

import com.cnu.lsrs.domain.Seat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** 쿼리 메서드는 필요한 사람이 "추가"만 한다. 다른 사람이 만든 메서드는 고치지 않는다 (README 참고). */
public interface SeatRepository extends JpaRepository<Seat, Long> {

	Optional<Seat> findBySeatCode(String seatCode);

	// ---- B (US-03~05) ----

	/** 좌석 그리드 표시 순서 (US-03) */
	List<Seat> findAllByOrderBySeatCodeAsc();

	/** 좌석 행 잠금 (SELECT … FOR UPDATE). 같은 좌석 동시 예약을 한 줄로 세운다 (US-04, NFR 4.4) */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from Seat s where s.id = :id")
	Optional<Seat> findByIdForUpdate(@Param("id") Long id);
}
