package com.t.tshow.infra.qdrant;

import com.t.tshow.domain.index.port.VectorIndex;
import com.t.tshow.global.config.IndexProperties;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 실제 Qdrant 와의 연동 시험 (payload 형식, 날짜·분류·지리 필터, payload 만 갱신, 삭제).
 * 로컬 Qdrant(docker compose up -d qdrant, gRPC 6336)가 떠 있을 때만 돈다:
 * <pre>RUN_QDRANT_IT=true ./gradlew.bat test --tests "*QdrantVectorIndexIT*" --no-daemon</pre>
 * 시험용 컬렉션을 만들고 끝나면 지운다.
 */
@EnabledIfEnvironmentVariable(named = "RUN_QDRANT_IT", matches = "true")
class QdrantVectorIndexIT {

    private static final int DIM = 4;
    private final String collection = "tshow-it-" + UUID.randomUUID();
    private QdrantClient client;
    private QdrantVectorIndex index;

    @BeforeEach
    void setUp() {
        String host = System.getenv().getOrDefault("QDRANT_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("QDRANT_GRPC_PORT", "6336"));
        client = new QdrantClient(QdrantGrpcClient.newBuilder(host, port, false).build());
        index = new QdrantVectorIndex(client, new IndexProperties(collection, DIM, 64, 600, "index/embedding.tpl", true));
        index.ensureCollection();
    }

    @AfterEach
    void tearDown() throws Exception {
        client.deleteCollectionAsync(collection).get();
        client.close();
    }

    private static Map<String, Object> payload(String category, String sido, LocalDate start, LocalDate end, String price, Double lat, Double lon) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("kind", "PERFORMANCE");
        p.put("category", category);
        p.put("sido", sido);
        p.put("start_day", start.toEpochDay());
        p.put("end_day", end.toEpochDay());
        p.put("price_type", price);
        p.put("has_description", true);
        p.put("source_count", 1L);
        if (lat != null) p.put("location", Map.of("lat", lat, "lon", lon));
        return p;
    }

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);
    private static final float[] V = {1, 0, 0, 0};

    @Test
    void 필수_조건을_먼저_걸고_그_안에서_벡터로_찾는다() {
        UUID seoulPlay = UUID.randomUUID(), seoulMusical = UUID.randomUUID(), busanPlay = UUID.randomUUID(), pastPlay = UUID.randomUUID();
        index.upsert(List.of(
                new VectorIndex.Point(seoulPlay, V, payload("theater", "11", TODAY.plusDays(2), TODAY.plusDays(2), "PAID", 37.5665, 126.9780)),
                new VectorIndex.Point(seoulMusical, new float[]{0.9f, 0.1f, 0, 0}, payload("musical", "11", TODAY.plusDays(3), TODAY.plusDays(9), "FREE", 37.5700, 126.9800)),
                new VectorIndex.Point(busanPlay, new float[]{0.95f, 0.05f, 0, 0}, payload("theater", "26", TODAY.plusDays(2), TODAY.plusDays(2), "PAID", 35.1796, 129.0756)),
                new VectorIndex.Point(pastPlay, V, payload("theater", "11", TODAY.minusDays(20), TODAY.minusDays(10), "PAID", 37.5665, 126.9780))));

        // 종료일이 오늘 이후인 것만 (이미 끝난 행사는 후보에도 안 들어온다)
        List<UUID> upcoming = ids(index.search(V, new VectorIndex.Filter(TODAY.toEpochDay(), null, null, null, null, null, null, null), 10));
        assertFalse(upcoming.contains(pastPlay));
        assertEquals(3, upcoming.size());

        // 지역 + 분류
        assertEquals(List.of(seoulPlay), ids(index.search(V,
                new VectorIndex.Filter(TODAY.toEpochDay(), null, null, List.of("theater"), "11", null, null, null), 10)));

        // 날짜 범위: 시작이 이번 주말(오늘+2) 이전인 것
        List<UUID> byStart = ids(index.search(V, new VectorIndex.Filter(TODAY.toEpochDay(), TODAY.plusDays(2).toEpochDay(), null, null, null, null, null, null), 10));
        assertTrue(byStart.contains(seoulPlay) && byStart.contains(busanPlay));
        assertFalse(byStart.contains(seoulMusical), "시작일이 3일 뒤라 제외");

        // 무료만
        assertEquals(List.of(seoulMusical), ids(index.search(V,
                new VectorIndex.Filter(null, null, null, null, null, null, "FREE", null), 10)));

        // 반경 5km: 서울 두 곳만, 부산은 제외
        List<UUID> near = ids(index.search(V, new VectorIndex.Filter(TODAY.toEpochDay(), null, null, null, null, null, null,
                new VectorIndex.Geo(37.5665, 126.9780, 5000)), 10));
        assertTrue(near.contains(seoulPlay) && near.contains(seoulMusical));
        assertFalse(near.contains(busanPlay));
    }

    @Test
    void payload_만_바꾸면_벡터는_그대로이고_검색_결과가_바뀐다() {
        UUID id = UUID.randomUUID();
        index.upsert(List.of(new VectorIndex.Point(id, V, payload("theater", "11", TODAY.plusDays(2), TODAY.plusDays(2), "PAID", 37.5, 127.0))));
        assertEquals(1, index.search(V, new VectorIndex.Filter(null, null, null, null, null, null, "PAID", null), 5).size());

        index.overwritePayload(id, payload("theater", "11", TODAY.plusDays(2), TODAY.plusDays(2), "FREE", null, null));

        assertEquals(0, index.search(V, new VectorIndex.Filter(null, null, null, null, null, null, "PAID", null), 5).size());
        List<VectorIndex.Hit> free = index.search(V, new VectorIndex.Filter(null, null, null, null, null, null, "FREE", null), 5);
        assertEquals(1, free.size());
        assertTrue(free.get(0).score() > 0.99, "벡터는 그대로라 같은 질의가 그대로 맞는다");
        // 좌표를 지우는 갱신이 반영됐는지(덮어쓰기라서 location 이 사라진다)
        assertEquals(0, index.search(V, new VectorIndex.Filter(null, null, null, null, null, null, null,
                new VectorIndex.Geo(37.5, 127.0, 1000)), 5).size());
    }

    @Test
    void 모든_id_조회와_삭제() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        index.upsert(List.of(
                new VectorIndex.Point(a, V, payload("theater", "11", TODAY, TODAY, "PAID", null, null)),
                new VectorIndex.Point(b, V, payload("theater", "11", TODAY, TODAY, "PAID", null, null))));
        assertEquals(java.util.Set.of(a, b), index.allIds());

        index.delete(List.of(a));
        assertEquals(java.util.Set.of(b), index.allIds());
    }

    private static List<UUID> ids(List<VectorIndex.Hit> hits) {
        return hits.stream().map(VectorIndex.Hit::id).toList();
    }
}
