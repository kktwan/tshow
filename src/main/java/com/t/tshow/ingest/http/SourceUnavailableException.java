package com.t.tshow.ingest.http;

/** 소스 API 호출이 재시도 후에도 실패했을 때 */
public class SourceUnavailableException extends RuntimeException {

    public SourceUnavailableException(String message) {
        super(message);
    }

    public SourceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
