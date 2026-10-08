package com.t.tshow.global.exception;

import org.springframework.http.HttpStatus;

/** API 가 돌려주는 오류의 종류와 HTTP 상태 */
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청이 올바르지 않아요"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "찾는 항목이 없어요"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "오늘 쓸 수 있는 횟수를 모두 썼어요"),
    EXTERNAL_API_FAILED(HttpStatus.BAD_GATEWAY, "외부 서비스 호출에 실패했어요"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에서 문제가 생겼어요");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
