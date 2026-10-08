package com.t.tshow.index;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 벡터 색인(Qdrant)에 대한 창구. 실제 구현은 {@link QdrantVectorIndex}, 테스트는 메모리 구현을 쓴다.
 * 점의 id 는 행사 id(UUID)와 같다.
 */
public interface VectorIndex {

    /** 색인에 올릴 점 하나: 행사 id, 벡터, 필터용 payload */
    record Point(UUID id, float[] vector, Map<String, Object> payload) {
    }

    /** 검색 결과 한 건 */
    record Hit(UUID id, double score) {
    }

    /** 검색 필터. null 인 조건은 걸지 않는다 */
    record Filter(Long endDayAtLeast, Long startDayAtMost, List<String> kinds, List<String> categories,
                  String sido, String sigungu, String priceType, Geo geo) {
        public static Filter none() {
            return new Filter(null, null, null, null, null, null, null, null);
        }
    }

    /** 위경도 반경 조건 */
    record Geo(double lat, double lon, double radiusMeters) {
    }

    /** 컬렉션이 없으면 만들고 필터용 payload 색인을 건다 */
    void ensureCollection();

    /** 점을 넣거나(같은 id 면 덮어씀) 갱신한다 */
    void upsert(List<Point> points);

    /** 벡터는 그대로 두고 payload 만 통째로 바꾼다 (재임베딩 없이 날짜·가격 등을 갱신) */
    void overwritePayload(UUID id, Map<String, Object> payload);

    void delete(Collection<UUID> ids);

    /** 색인에 있는 모든 점의 id */
    Set<UUID> allIds();

    /** 필터를 먼저 적용한 뒤 그 안에서 벡터 유사도로 찾는다 */
    List<Hit> search(float[] vector, Filter filter, int limit);
}
