package com.t.tshow.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 사이트 안내 설정 (application.yml 의 tshow.site.*).
 *
 * @param contactEmail 정정·삭제 요청을 받을 이메일. 비워 두면 화면 하단에 연락처를 보이지 않는다
 * @param baseUrl      서비스 주소(끝의 / 없이). 검색엔진용 canonical·sitemap 주소를 만들 때 쓴다
 */
@ConfigurationProperties(prefix = "tshow.site")
public record SiteProperties(@DefaultValue("") String contactEmail, @DefaultValue("https://tshow.duckdns.org") String baseUrl) {
}
