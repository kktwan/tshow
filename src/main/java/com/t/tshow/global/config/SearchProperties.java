package com.t.tshow.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;
import java.util.Map;

/**
 * 검색 설정 (application.yml 의 tshow.search.*). 점수 하한·후보 수·화면 크기는 코드에 박지 않고 여기서 조정한다.
 *
 * @param candidateLimit     한 번의 검색에서 벡터 색인에 요청할 최대 후보 수 (이 안에서 쪽 나누기)
 * @param minScore           벡터 유사도가 이 값보다 낮은 후보는 관련 없는 것으로 보고 버린다 (관련 없는 질의에는 결과를 비운다)
 * @param pageSize           한 쪽에 보여 줄 행사 수
 * @param maxPageSize        API 가 허용하는 한 쪽 최대 크기
 * @param maxQueryLength     검색어 최대 글자 수 (넘는 부분은 자른다. 임베딩 비용과 남용을 막는다)
 * @param defaultRadiusKm    "내 주변" 기본 반경(km)
 * @param radiusOptionsKm    화면에서 고를 수 있는 반경(km)
 * @param quickDates         화면에 버튼으로 보일 날짜 규칙 (사전의 규칙 이름)
 * @param examples           검색창 아래에 보여 줄 검색어 예시 (눌러 보면 바로 검색된다)
 * @param zone               "오늘", "이번 주말" 같은 날짜 표현의 기준 시간대
 * @param sourceNames        출처 표기에 쓸 소스 이름 (SourceType 이름 → 화면에 보일 이름)
 */
@ConfigurationProperties(prefix = "tshow.search")
public record SearchProperties(
        @DefaultValue("60") int candidateLimit,
        @DefaultValue("0.0") double minScore,
        @DefaultValue("12") int pageSize,
        @DefaultValue("48") int maxPageSize,
        @DefaultValue("100") int maxQueryLength,
        @DefaultValue("5") int defaultRadiusKm,
        @DefaultValue({"1", "3", "5", "10"}) List<Integer> radiusOptionsKm,
        @DefaultValue({"today", "tomorrow", "weekend", "this-month"}) List<String> quickDates,
        @DefaultValue List<String> examples,
        @DefaultValue("Asia/Seoul") String zone,
        @DefaultValue Map<String, String> sourceNames) {
}
