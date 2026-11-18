package com.cnu.lsrs.repository;

import com.cnu.lsrs.domain.Reservation;
import com.cnu.lsrs.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 쿼리 메서드는 필요한 사람이 "추가"만 한다. 다른 사람이 만든 메서드는 고치지 않는다 (README 참고). */
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Reservation r where r.qrToken = :qrToken")
	Optional<Reservation> findByQrTokenForUpdate(@Param("qrToken") String qrToken);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Reservation r where r.id = :id")
	Optional<Reservation> findByIdForUpdate(@Param("id") Long id);

	@EntityGraph(attributePaths = {"seat", "user"})
	List<Reservation> findAllByStatusInAndStartAtLessThanEqualAndEndAtGreaterThan(
			List<ReservationStatus> statuses, LocalDateTime startAt, LocalDateTime endAt);

	@EntityGraph(attributePaths = {"seat", "user"})
	List<Reservation> findAllByUser_IdOrderByCreatedAtDesc(Long userId);

	@EntityGraph(attributePaths = {"seat", "user"})
	List<Reservation> findAllBySeat_SeatCodeOrderByCreatedAtDesc(String seatCode);

	@EntityGraph(attributePaths = {"seat", "user"})
	List<Reservation> findAllByStartAtLessThanAndEndAtGreaterThanAndStatusIn(
			LocalDateTime endAt, LocalDateTime startAt, List<ReservationStatus> statuses);

	@EntityGraph(attributePaths = {"seat", "user"})
	List<Reservation> findAllByStartAtLessThanAndEndAtGreaterThanAndCheckedInAtIsNotNull(
			LocalDateTime endAt, LocalDateTime startAt);
}
