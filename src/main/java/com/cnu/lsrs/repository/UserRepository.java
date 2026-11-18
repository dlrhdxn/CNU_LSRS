package com.cnu.lsrs.repository;

import com.cnu.lsrs.domain.User;
import com.cnu.lsrs.domain.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

/** 쿼리 메서드는 필요한 사람이 "추가"만 한다. 다른 사람이 만든 메서드는 고치지 않는다 (README 참고). */
public interface UserRepository extends JpaRepository<User, Long> {

	/** 로그인 ID(학번 또는 관리자·단말 ID)로 조회 (FR-U-10) */
	Optional<User> findByStudentNo(String studentNo);

	List<User> findAllByRoleOrderByStudentNoAsc(Role role);

	// ---- B (US-04) ----

	/** 사용자 행 잠금. 같은 학생이 다른 좌석으로 동시에 예약해도 시간 겹침 검사가 한 줄로 서게 한다 (FR-R-11) */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from User u where u.id = :id")
	Optional<User> findByIdForUpdate(@Param("id") Long id);
}
