package com.t.tshow.infra.http;

/** 외부 API 호출이 재시도 후에도 실패했을 때 */
public class ApiCallException extends RuntimeException {

    public ApiCallException(String message) {
        super(message);
    }

    public ApiCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
