package com.t.tshow.global.exception;

/** 서비스 계층에서 의도적으로 던지는 오류. 핸들러가 {@link ErrorCode} 의 상태와 메시지로 응답한다 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
