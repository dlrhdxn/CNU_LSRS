package com.cnu.lsrs.security;

import com.cnu.lsrs.domain.Role;
import com.cnu.lsrs.domain.User;
import com.cnu.lsrs.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

	public static final String ACCESS_COOKIE = "access_token";

	private final JwtProvider jwtProvider;
	private final UserRepository userRepository;

	public JwtAuthFilter(JwtProvider jwtProvider, UserRepository userRepository) {
		this.jwtProvider = jwtProvider;
		this.userRepository = userRepository;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (SecurityContextHolder.getContext().getAuthentication() == null) {
			String token = getBearerToken(request);
			if (token == null) {
				token = getCookieToken(request);
			}
			if (token != null) {
				try {
					Claims claims = jwtProvider.parse(token);
					if (!"access".equals(claims.get("type", String.class))) {
						filterChain.doFilter(request, response);
						return;
					}
					Long userId = Long.valueOf(claims.getSubject());
					User user = userRepository.findById(userId).orElse(null);
					if (user != null && user.isActive()) {
						LoginUser principal = LoginUser.from(user);
						UsernamePasswordAuthenticationToken authentication =
								new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
						authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
						SecurityContextHolder.getContext().setAuthentication(authentication);
					}
				} catch (RuntimeException ignored) {
					SecurityContextHolder.clearContext();
				}
			}
		}
		filterChain.doFilter(request, response);
	}

	private String getBearerToken(HttpServletRequest request) {
		String authorization = request.getHeader("Authorization");
		if (authorization != null && authorization.startsWith("Bearer ")) {
			return authorization.substring(7);
		}
		return null;
	}

	private String getCookieToken(HttpServletRequest request) {
		if (request.getCookies() == null) {
			return null;
		}
		for (Cookie cookie : request.getCookies()) {
			if (ACCESS_COOKIE.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}
}
