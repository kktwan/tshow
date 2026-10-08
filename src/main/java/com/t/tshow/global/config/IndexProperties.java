package com.t.tshow.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 벡터 색인 설정 (application.yml 의 tshow.index.*).
 *
 * @param collectionName      Qdrant 컬렉션 이름 (공용 Qdrant 안에서 tshow 전용)
 * @param vectorDimensions    벡터 크기. spring.ai.openai.embedding.dimensions 와 같아야 한다
 * @param batchSize           임베딩 호출 한 번에 보낼 문장 수
 * @param descriptionMaxChars 임베딩 텍스트에 넣을 설명의 최대 글자 수
 * @param embeddingTemplate   임베딩 텍스트 모양을 적은 리소스 경로. {필드} 를 채우고 값이 없는 줄은 뺀다
 * @param enabled             false 면 수집 뒤 색인 단계를 건너뛴다
 */
@ConfigurationProperties(prefix = "tshow.index")
public record IndexProperties(
        @DefaultValue("tshow-events") String collectionName,
        @DefaultValue("768") int vectorDimensions,
        @DefaultValue("64") int batchSize,
        @DefaultValue("600") int descriptionMaxChars,
        @DefaultValue("index/embedding.tpl") String embeddingTemplate,
        @DefaultValue("true") boolean enabled) {
}
