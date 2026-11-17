package com.cnu.lsrs.repository;

import com.cnu.lsrs.domain.Reservation;
import com.cnu.lsrs.domain.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 쿼리 메서드는 필요한 사람이 "추가"만 한다. 다른 사람이 만든 메서드는 고치지 않는다 (README 참고). */
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

	// ---- B (US-03~05) ----
	// 시간 겹침 조건은 Reservation.overlaps()와 같다: [startAt, endAt)와 [from, to)가 겹치면 start < to && from < end

	/** [from, to)와 겹치는 statuses 상태의 예약 전체 (좌석 조회 US-03). (seat_id, start_at) 인덱스 사용 */
	@Query("select r from Reservation r where r.status in :statuses and r.startAt < :to and r.endAt > :from")
	List<Reservation> findOverlapping(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
									  @Param("statuses") Collection<ReservationStatus> statuses);

	/** 해당 좌석에 [from, to)와 겹치는 statuses 상태의 예약이 있는지 (US-04 좌석 중복) */
	@Query("select count(r) > 0 from Reservation r where r.seat.id = :seatId and r.status in :statuses"
			+ " and r.startAt < :to and r.endAt > :from")
	boolean existsSeatOverlap(@Param("seatId") Long seatId, @Param("from") LocalDateTime from,
							  @Param("to") LocalDateTime to, @Param("statuses") Collection<ReservationStatus> statuses);

	/** 해당 학생에게 [from, to)와 겹치는 statuses 상태의 예약이 있는지 (FR-R-11 본인 중복) */
	@Query("select count(r) > 0 from Reservation r where r.user.id = :userId and r.status in :statuses"
			+ " and r.startAt < :to and r.endAt > :from")
	boolean existsUserOverlap(@Param("userId") Long userId, @Param("from") LocalDateTime from,
							  @Param("to") LocalDateTime to, @Param("statuses") Collection<ReservationStatus> statuses);

	/** 예약 행 잠금 조회 (US-05 취소와 체크인이 동시에 일어나는 경우 대비) */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Reservation r join fetch r.seat where r.id = :id")
	Optional<Reservation> findByIdWithSeatForUpdate(@Param("id") Long id);
}
