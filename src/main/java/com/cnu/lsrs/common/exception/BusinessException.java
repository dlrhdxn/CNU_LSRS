package com.cnu.lsrs.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 업무 규칙 위반을 사용자에게 알리는 예외. GlobalExceptionHandler가 {code, message} JSON으로 바꿔 응답한다.
 * code는 각 기능 담당자가 문자열로 정한다 (예: "SEAT_ALREADY_RESERVED").
 * 공용 enum을 두지 않는 이유: 여러 사람이 같은 파일에 코드를 추가하면 병합 충돌이 나기 때문.
 */
public class BusinessException extends RuntimeException {

	private final HttpStatus status;
	private final String code;

	public BusinessException(HttpStatus status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	public static BusinessException badRequest(String code, String message) {
		return new BusinessException(HttpStatus.BAD_REQUEST, code, message);
	}

	public static BusinessException forbidden(String code, String message) {
		return new BusinessException(HttpStatus.FORBIDDEN, code, message);
	}

	public static BusinessException notFound(String code, String message) {
		return new BusinessException(HttpStatus.NOT_FOUND, code, message);
	}

	public static BusinessException conflict(String code, String message) {
		return new BusinessException(HttpStatus.CONFLICT, code, message);
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}
}
