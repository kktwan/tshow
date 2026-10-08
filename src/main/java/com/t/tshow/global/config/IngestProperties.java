package com.t.tshow.global.config;


import com.t.tshow.infra.http.ApiPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * 수집 설정 (application.yml 의 tshow.ingest.*). 주소·호출 간격·재시도·범위는 코드에 박지 않고 여기서 조정한다.
 *
 * @param horizonMonths      오늘부터 몇 개월 앞까지 수집할지
 * @param refetchAfterHours  이미 수집한 항목의 상세를 이 시간 안에는 다시 조회하지 않는다 (증분 수집)
 * @param onStartup          앱 시작 때 수집을 한 번 실행할지 (로컬 확인용)
 * @param cron               정기 수집 시각
 * @param zone               소스가 시각을 줄 때 기준이 되는 시간대
 * @param appName            TourAPI 가 요구하는 서비스(앱) 이름
 * @param sources            수집할 소스 (KOPIS, CULTURE, TOURAPI)
 * @param dateFormats        소스가 주는 날짜 문자열 형식들 (2026.10.21 / 20261016 / 2026-10-16)
 * @param missingRunsBeforeDelete 성공한 수집에서 연속으로 이 횟수만큼 제공처 목록에 없던 항목은 제공처가 지운 것으로 보고 삭제한다
 * @param minListedRatio     이번 수집의 목록이 있어야 할 항목 수의 이 비율보다 적으면 API 장애로 보고 삭제 판단을 건너뛴다 (대량 삭제 방지)
 * @param upgradeImageHttps  이미지 주소가 http:// 이면 https:// 로 바꿔 저장한다 (혼합 콘텐츠 방지)
 */
@ConfigurationProperties(prefix = "tshow.ingest")
public record IngestProperties(
        @DefaultValue("6") int horizonMonths,
        @DefaultValue("24") int refetchAfterHours,
        @DefaultValue("false") boolean onStartup,
        @DefaultValue("0 0 3 * * *") String cron,
        @DefaultValue("Asia/Seoul") String zone,
        @DefaultValue("tshow") String appName,
        @DefaultValue({"KOPIS", "CULTURE", "TOURAPI"}) List<String> sources,
        @DefaultValue({"yyyy.MM.dd", "yyyyMMdd", "yyyy-MM-dd"}) List<String> dateFormats,
        @DefaultValue("true") boolean upgradeImageHttps,
        @DefaultValue("3") int missingRunsBeforeDelete,
        @DefaultValue("0.5") double minListedRatio,
        @DefaultValue Source kopis,
        @DefaultValue Source culture,
        @DefaultValue Source tourapi) {

    /**
     * 소스별 호출 설정.
     *
     * @param windowDays     한 번에 조회할 수 있는 최대 기간(일)
     * @param intervalMillis 호출 사이 최소 간격(ms)
     * @param backoffMillis  재시도 전 대기(ms). 시도 횟수만큼 곱한다
     * @param lookbackDays   시작일 기준으로만 조회되는 소스에서 진행 중인 행사를 얻기 위해 과거로 되돌려 조회할 일수
     */
    public record Source(
            @DefaultValue("") String baseUrl,
            @DefaultValue("30") int windowDays,
            @DefaultValue("100") int pageSize,
            @DefaultValue("500") long intervalMillis,
            @DefaultValue("4") int maxRetries,
            @DefaultValue("5000") long backoffMillis,
            @DefaultValue("30") int timeoutSeconds,
            @DefaultValue("0") int lookbackDays) {

        /** 이 소스를 호출할 때 지킬 규칙 (간격·재시도·제한 시간) */
        public ApiPolicy policy() {
            return new ApiPolicy(intervalMillis, maxRetries, backoffMillis, timeoutSeconds);
        }
    }
}
