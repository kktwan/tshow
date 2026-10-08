package com.t.tshow.global.config;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;
import java.util.Map;

/**
 * 소스 간 중복 판정과 필드 병합 설정 (application.yml 의 tshow.merge.*).
 * 판정 기준값과 필드별 소스 우선순위는 코드에 박지 않고 여기서 조정한다.
 *
 * @param titleSimilarity       제목(정규화한 것)이 이 값 이상 비슷하면 같은 제목으로 본다 (0~1, 글자 2-그램 Dice 계수)
 * @param venueSimilarity       장소 이름이 이 값 이상 비슷하면 같은 장소로 본다
 * @param maxDistanceMeters     장소 이름이 달라도 좌표가 이 거리(m) 안이면 같은 장소로 본다
 * @param dateToleranceDays     시작일이 이 일수 안으로 다르면 같은 시작으로 본다 (종료일은 같을 필요 없이 기간이 겹치면 된다 — 장기 공연은 소스마다 종료일을 다르게 준다)
 * @param containedTitleMinLength 장소가 같을 때, 한쪽 제목(정규화)이 다른 쪽에 통째로 포함되고 그 글자 수가 이 값 이상이면 같은 제목으로 본다 (부제가 한쪽에만 붙은 경우)
 * @param sameRegionTitleSimilarity 장소 이름·좌표가 맞지 않아도, 같은 시군구에서 제목이 이 값 이상 비슷하면 같은 행사로 본다 (같은 건물을 다른 이름으로 부르는 경우)
 * @param noPlaceTitleSimilarity 한쪽에 장소 정보(이름·좌표)가 없을 때 요구하는 더 엄격한 제목 유사도
 * @param priority              필드별 소스 우선순위 (앞에 있는 소스의 값을 우선 쓴다). 없는 필드는 default
 * @param retentionDays         종료 후 이 일수가 지나면 논리삭제한다 (검색·색인에서 빠진다)
 * @param purgeDays             종료 후 이 일수가 지나면 DB 에서도 완전히 지운다 (소스 레코드 포함). retentionDays 보다 커야 한다
 */
@ConfigurationProperties(prefix = "tshow.merge")
public record MergeProperties(
        @DefaultValue("0.85") double titleSimilarity,
        @DefaultValue("0.6") double venueSimilarity,
        @DefaultValue("300") double maxDistanceMeters,
        @DefaultValue("0") int dateToleranceDays,
        @DefaultValue("8") int containedTitleMinLength,
        @DefaultValue("0.95") double sameRegionTitleSimilarity,
        @DefaultValue("1.0") double noPlaceTitleSimilarity,
        @DefaultValue Map<String, List<String>> priority,
        @DefaultValue("30") int retentionDays,
        @DefaultValue("180") int purgeDays) {

    /** 필드의 소스 우선순위. 지정이 없으면 default, 그것도 없으면 빈 목록 (그때는 모든 소스를 같은 순위로 본다) */
    public List<String> priorityOf(String field) {
        List<String> specific = priority.get(field);
        if (specific != null && !specific.isEmpty()) return specific;
        return priority.getOrDefault("default", List.of());
    }
}
