package com.cnu.lsrs.security;

import com.cnu.lsrs.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 로그인 ID(학번·관리자 ID)로 사용자를 찾아 Spring Security에 넘긴다 (FR-U-10). */
@Service
public class LoginUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	public LoginUserDetailsService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
		return userRepository.findByStudentNo(loginId)
				.map(LoginUser::from)
				.orElseThrow(() -> new UsernameNotFoundException("존재하지 않는 계정입니다."));
	}
}
