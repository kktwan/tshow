package com.t.tshow.global.config;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * 정규화 규칙 설정 (application.yml 의 tshow.normalize.*).
 *
 * @param titleStripPatterns 제목 비교용 문자열에서 지울 패턴: [지역] 꼬리표, 괄호 부가 문구, 제목에 붙은 8자리 날짜
 * @param venueStripPatterns 장소 이름 비교용 문자열에서 지울 패턴: 괄호 부가 문구 (예: 시흥아트센터 (대공연장))
 */
@ConfigurationProperties(prefix = "tshow.normalize")
public record NormalizeProperties(
        @DefaultValue({"\\[[^\\]]*\\]", "\\([^)]*\\)", "(?<!\\d)\\d{8}(?!\\d)"}) List<String> titleStripPatterns,
        @DefaultValue({"\\([^)]*\\)"}) List<String> venueStripPatterns) {
}
