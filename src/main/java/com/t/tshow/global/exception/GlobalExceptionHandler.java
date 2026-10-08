package com.t.tshow.global.exception;

import com.t.tshow.global.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** REST API 의 예외를 {@link ApiResponse#error} 형식으로 바꾼다 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> business(BusinessException e) {
        return respond(e.errorCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ApiResponse<Void>> invalid(Exception e) {
        return respond(ErrorCode.INVALID_REQUEST, ErrorCode.INVALID_REQUEST.message());
    }

    /** 예상하지 못한 오류. 내용은 로그에만 남기고 응답에는 일반 메시지만 쓴다 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception e) {
        log.error("처리하지 못한 오류", e);
        return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.message());
    }

    private static ResponseEntity<ApiResponse<Void>> respond(ErrorCode code, String message) {
        return ResponseEntity.status(code.status()).body(ApiResponse.error(message));
    }
}
