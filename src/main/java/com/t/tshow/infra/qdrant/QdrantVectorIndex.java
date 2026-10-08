package com.t.tshow.infra.qdrant;

import com.t.tshow.domain.index.port.VectorIndex;
import com.t.tshow.domain.index.service.PayloadBuilder;
import com.t.tshow.global.config.IndexProperties;

import io.qdrant.client.ConditionFactory;
import io.qdrant.client.PointIdFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.VectorsFactory;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.PayloadSchemaType;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Common.Condition;
import io.qdrant.client.grpc.Common.PointId;
import io.qdrant.client.grpc.Common.Range;
import io.qdrant.client.grpc.JsonWithInt.Struct;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.PointsSelector;
import io.qdrant.client.grpc.Points.PointsIdsList;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.ScrollPoints;
import io.qdrant.client.grpc.Points.ScrollResponse;
import io.qdrant.client.grpc.Points.SearchPoints;
import io.qdrant.client.grpc.Points.WithPayloadSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * Qdrant 구현. 공용 Qdrant 안의 tshow 전용 컬렉션 하나를 쓴다.
 * 점 id 는 행사 id(UUID), payload 는 필터용 값만 담는다 (docs/index-design.md 2절).
 */
@Component
public class QdrantVectorIndex implements VectorIndex {

    private static final Logger log = LoggerFactory.getLogger(QdrantVectorIndex.class);
    private static final int SCROLL_PAGE_SIZE = 1000;

    private final QdrantClient client;
    private final String collection;
    private final int dimensions;

    public QdrantVectorIndex(QdrantClient client, IndexProperties properties) {
        this.client = client;
        this.collection = properties.collectionName();
        this.dimensions = properties.vectorDimensions();
    }

    @Override
    public void ensureCollection() {
        call(() -> {
            if (client.collectionExistsAsync(collection).get()) return null;
            client.createCollectionAsync(collection,
                    VectorParams.newBuilder().setDistance(Distance.Cosine).setSize(dimensions).build()).get();
            // 필터에 쓰는 필드에 색인을 걸어 필터가 빠르게 한다
            for (String keyword : List.of(PayloadBuilder.KIND, PayloadBuilder.CATEGORY, PayloadBuilder.SIDO,
                    PayloadBuilder.SIGUNGU, PayloadBuilder.PRICE_TYPE)) {
                client.createPayloadIndexAsync(collection, keyword, PayloadSchemaType.Keyword, null, null, null, null).get();
            }
            for (String integer : List.of(PayloadBuilder.START_DAY, PayloadBuilder.END_DAY)) {
                client.createPayloadIndexAsync(collection, integer, PayloadSchemaType.Integer, null, null, null, null).get();
            }
            client.createPayloadIndexAsync(collection, PayloadBuilder.LOCATION, PayloadSchemaType.Geo, null, null, null, null).get();
            log.info("Qdrant 컬렉션 {} 을 만들었어요 (벡터 {}차원, 코사인)", collection, dimensions);
            return null;
        });
    }

    @Override
    public void upsert(List<Point> points) {
        if (points.isEmpty()) return;
        List<PointStruct> structs = new ArrayList<>();
        for (Point p : points) {
            structs.add(PointStruct.newBuilder()
                    .setId(PointIdFactory.id(p.id()))
                    .setVectors(VectorsFactory.vectors(p.vector()))
                    .putAllPayload(toPayload(p.payload()))
                    .build());
        }
        call(() -> client.upsertAsync(collection, structs).get());
    }

    @Override
    public void overwritePayload(UUID id, Map<String, Object> payload) {
        PointsSelector selector = PointsSelector.newBuilder()
                .setPoints(PointsIdsList.newBuilder().addIds(PointIdFactory.id(id))).build();
        call(() -> client.overwritePayloadAsync(collection, toPayload(payload), selector, true, null, null).get());
    }

    @Override
    public void delete(Collection<UUID> ids) {
        if (ids.isEmpty()) return;
        List<PointId> pointIds = ids.stream().map(PointIdFactory::id).toList();
        call(() -> client.deleteAsync(collection, pointIds).get());
    }

    @Override
    public Set<UUID> allIds() {
        Set<UUID> ids = new HashSet<>();
        PointId offset = null;
        while (true) {
            ScrollPoints.Builder request = ScrollPoints.newBuilder().setCollectionName(collection).setLimit(SCROLL_PAGE_SIZE)
                    .setWithPayload(WithPayloadSelector.newBuilder().setEnable(false));
            if (offset != null) request.setOffset(offset);
            ScrollResponse response = call(() -> client.scrollAsync(request.build()).get());
            response.getResultList().forEach(p -> ids.add(UUID.fromString(p.getId().getUuid())));
            if (!response.hasNextPageOffset() || response.getResultCount() == 0) break;
            offset = response.getNextPageOffset();
        }
        return ids;
    }

    @Override
    public List<Hit> search(float[] vector, Filter filter, int limit) {
        SearchPoints.Builder request = SearchPoints.newBuilder().setCollectionName(collection).setLimit(limit);
        for (float v : vector) request.addVector(v);
        io.qdrant.client.grpc.Common.Filter qdrantFilter = toFilter(filter);
        if (qdrantFilter != null) request.setFilter(qdrantFilter);
        List<ScoredPoint> found = call(() -> client.searchAsync(request.build()).get());
        return found.stream().map(p -> new Hit(UUID.fromString(p.getId().getUuid()), p.getScore())).toList();
    }

    /** 필수 조건을 모두 Qdrant 필터로 바꾼다. 조건이 하나도 없으면 null */
    static io.qdrant.client.grpc.Common.Filter toFilter(Filter f) {
        if (f == null) return null;
        List<Condition> must = new ArrayList<>();
        if (f.endDayAtLeast() != null) {
            must.add(ConditionFactory.range(PayloadBuilder.END_DAY, Range.newBuilder().setGte(f.endDayAtLeast()).build()));
        }
        if (f.startDayAtMost() != null) {
            must.add(ConditionFactory.range(PayloadBuilder.START_DAY, Range.newBuilder().setLte(f.startDayAtMost()).build()));
        }
        if (f.kinds() != null && !f.kinds().isEmpty()) must.add(ConditionFactory.matchKeywords(PayloadBuilder.KIND, f.kinds()));
        if (f.categories() != null && !f.categories().isEmpty()) {
            must.add(ConditionFactory.matchKeywords(PayloadBuilder.CATEGORY, f.categories()));
        }
        if (f.sido() != null) must.add(ConditionFactory.matchKeyword(PayloadBuilder.SIDO, f.sido()));
        if (f.sigungu() != null) must.add(ConditionFactory.matchKeyword(PayloadBuilder.SIGUNGU, f.sigungu()));
        if (f.priceType() != null) must.add(ConditionFactory.matchKeyword(PayloadBuilder.PRICE_TYPE, f.priceType()));
        if (f.geo() != null) {
            must.add(ConditionFactory.geoRadius(PayloadBuilder.LOCATION, f.geo().lat(), f.geo().lon(), (float) f.geo().radiusMeters()));
        }
        if (must.isEmpty()) return null;
        return io.qdrant.client.grpc.Common.Filter.newBuilder().addAllMust(must).build();
    }

    private static Map<String, Value> toPayload(Map<String, Object> payload) {
        Map<String, Value> result = new HashMap<>();
        payload.forEach((key, value) -> result.put(key, toValue(value)));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Value toValue(Object v) {
        if (v instanceof String s) return ValueFactory.value(s);
        if (v instanceof Boolean b) return ValueFactory.value(b);
        if (v instanceof Long l) return ValueFactory.value(l);
        if (v instanceof Integer i) return ValueFactory.value(i.longValue());
        if (v instanceof Double d) return ValueFactory.value(d);
        if (v instanceof Map<?, ?> m) {
            // 위경도 같은 하위 객체 (Qdrant 의 geo payload 는 {lat, lon} 객체)
            Struct.Builder struct = Struct.newBuilder();
            ((Map<String, Object>) m).forEach((k, x) -> struct.putFields(k, toValue(x)));
            return Value.newBuilder().setStructValue(struct).build();
        }
        throw new IllegalArgumentException("payload 에 지원하지 않는 값 형식: " + v.getClass());
    }

    private <T> T call(QdrantCall<T> call) {
        try {
            return call.run();
        } catch (ExecutionException e) {
            throw new IllegalStateException("Qdrant 호출 실패: " + (e.getCause() == null ? e.getMessage() : e.getCause().getMessage()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Qdrant 호출 중단됨", e);
        }
    }

    @FunctionalInterface
    private interface QdrantCall<T> {
        T run() throws ExecutionException, InterruptedException;
    }
}
