package com.cnu.lsrs.common.exception;

/** 모든 API 오류 응답의 공통 형식. 화면(JS)은 message를 그대로 사용자에게 보여준다. */
public record ErrorResponse(String code, String message) {
}
