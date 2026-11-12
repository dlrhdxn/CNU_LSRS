package com.cnu.lsrs.repository;

import com.cnu.lsrs.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 쿼리 메서드는 필요한 사람이 "추가"만 한다. 다른 사람이 만든 메서드는 고치지 않는다 (README 참고). */
public interface UserRepository extends JpaRepository<User, Long> {

	/** 로그인 ID(학번 또는 관리자·단말 ID)로 조회 (FR-U-10) */
	Optional<User> findByStudentNo(String studentNo);
}
