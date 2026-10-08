package com.t.tshow.global.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 요청한 사람의 IP. 앱은 nginx 뒤에서만 접근되고 nginx 가 X-Real-IP 를 실제 접속자 주소로 덮어쓰므로 그 값을 쓴다.
 * 헤더가 없으면(로컬 실행 등) 연결 주소를 쓴다.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String header = request.getHeader("X-Real-IP");
        return header != null && !header.isBlank() ? header.trim() : request.getRemoteAddr();
    }
}
