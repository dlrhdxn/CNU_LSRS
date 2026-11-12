package com.cnu.lsrs.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 컨트롤러에서 던진 예외를 {code, message} JSON으로 통일한다. */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
		return ResponseEntity.status(e.getStatus()).body(new ErrorResponse(e.getCode(), e.getMessage()));
	}

	/** @Valid 검증 실패: 첫 번째 필드 오류 메시지를 안내한다 (예: 비밀번호 규칙, FR-U-05) */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
		FieldError fieldError = e.getBindingResult().getFieldError();
		String message = fieldError != null ? fieldError.getDefaultMessage() : "입력값이 올바르지 않습니다.";
		return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_INPUT", message));
	}

	@ExceptionHandler({MissingServletRequestParameterException.class,
			MethodArgumentTypeMismatchException.class,
			HttpMessageNotReadableException.class})
	public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
		return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_INPUT", "요청 형식이 올바르지 않습니다."));
	}

	/** 엔티티가 막은 잘못된 상태 전이 (예: 이미 취소된 예약 체크인) */
	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException e) {
		log.warn("Invalid state transition: {}", e.getMessage());
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("INVALID_STATE", e.getMessage()));
	}

	/** 엔티티가 막은 잘못된 값 (예: 시작 시각 >= 종료 시각) */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
		log.warn("Invalid argument: {}", e.getMessage());
		return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_INPUT", e.getMessage()));
	}
}
