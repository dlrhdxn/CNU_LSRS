package com.cnu.lsrs.repository;

import com.cnu.lsrs.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

/** 쿼리 메서드는 필요한 사람이 "추가"만 한다. 다른 사람이 만든 메서드는 고치지 않는다 (README 참고). */
public interface ReservationRepository extends JpaRepository<Reservation, Long> {
}
