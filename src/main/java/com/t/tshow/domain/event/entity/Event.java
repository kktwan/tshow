package com.t.tshow.domain.event.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 병합된 행사 하나 (event 테이블의 한 행). 여러 소스의 같은 행사를 합친 결과이며 검색·색인은 이것을 쓴다.
 * id 는 병합 계획 단계에서 정해지고 벡터 색인의 점 id 로도 쓰인다. dataHash 는 내용 필드의 해시로,
 * 바뀐 행사만 갱신하고 색인 변경분을 가리는 데 쓴다. 색인 상태(embedHash, payloadHash, indexedAt)와 논리삭제(archivedAt)는
 * 병합이 아니라 각각 색인·보관 단계가 관리한다.
 */
@Entity
@Table(name = "event")
@DynamicUpdate
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Event implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false, length = 20)
    private String kind;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(nullable = false)
    private String title;

    @Column(name = "title_norm", nullable = false)
    private String titleNorm;

    private String description;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "venue_name")
    private String venueName;

    @Column(name = "venue_name_norm")
    private String venueNameNorm;

    private String address;

    @Column(name = "sido_code", length = 10)
    private String sidoCode;

    @Column(name = "sigungu_code", length = 10)
    private String sigunguCode;

    private Double lat;

    private Double lon;

    @Column(name = "price_type", nullable = false, length = 10)
    private String priceType;

    @Column(name = "price_text")
    private String priceText;

    @Column(name = "age_text")
    private String ageText;

    @Column(name = "runtime_text")
    private String runtimeText;

    @Column(name = "schedule_text")
    private String scheduleText;

    @Column(name = "cast_text")
    private String castText;

    @Column(name = "host_text")
    private String hostText;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "image_license", length = 40)
    private String imageLicense;

    @Column(name = "info_url")
    private String infoUrl;

    /** 예매처 목록 JSON (jsonb). 문자열 그대로 읽고 쓴다 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ticket_links", columnDefinition = "jsonb")
    private String ticketLinksJson;

    @Column(name = "source_count", nullable = false)
    private int sourceCount;

    @Column(name = "has_description", nullable = false)
    private boolean hasDescription;

    @Column(name = "data_hash", nullable = false, length = 64)
    private String dataHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 종료 후 보관 기간이 지나 논리삭제된 시각 (Qdrant 는 같은 시점에 물리 삭제) */
    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "embed_hash", length = 64)
    private String embedHash;

    @Column(name = "payload_hash", length = 64)
    private String payloadHash;

    /** 벡터 색인에 올라가 있는 행사만 값이 있다 */
    @Column(name = "indexed_at")
    private Instant indexedAt;

    /** 아직 저장되지 않은 새 행사인지. id 를 미리 정하므로 Spring Data 가 새 행사임을 알 수 있게 직접 알려준다 */
    @Transient
    @Getter(AccessLevel.NONE)
    @Builder.Default
    private boolean newEntity = false;

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        this.newEntity = false;
    }

    /** 처음 저장되는 행사로 표시하고 생성·수정 시각을 기록한다 */
    public void created(Instant now) {
        this.newEntity = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** 병합을 다시 돌려 내용이 바뀐 행사에 새 병합 결과를 반영한다 (색인 상태·보관 상태는 건드리지 않는다) */
    public void applyMerged(Event m, Instant now) {
        this.kind = m.kind;
        this.category = m.category;
        this.title = m.title;
        this.titleNorm = m.titleNorm;
        this.description = m.description;
        this.startDate = m.startDate;
        this.endDate = m.endDate;
        this.venueName = m.venueName;
        this.venueNameNorm = m.venueNameNorm;
        this.address = m.address;
        this.sidoCode = m.sidoCode;
        this.sigunguCode = m.sigunguCode;
        this.lat = m.lat;
        this.lon = m.lon;
        this.priceType = m.priceType;
        this.priceText = m.priceText;
        this.ageText = m.ageText;
        this.runtimeText = m.runtimeText;
        this.scheduleText = m.scheduleText;
        this.castText = m.castText;
        this.hostText = m.hostText;
        this.imageUrl = m.imageUrl;
        this.imageLicense = m.imageLicense;
        this.infoUrl = m.infoUrl;
        this.ticketLinksJson = m.ticketLinksJson;
        this.sourceCount = m.sourceCount;
        this.hasDescription = m.hasDescription;
        this.dataHash = m.dataHash;
        this.updatedAt = now;
    }

    public boolean isIndexed() {
        return indexedAt != null;
    }

    public void markIndexed(String embedHash, String payloadHash, Instant now) {
        this.embedHash = embedHash;
        this.payloadHash = payloadHash;
        this.indexedAt = now;
    }

    public void clearIndexed() {
        this.embedHash = null;
        this.payloadHash = null;
        this.indexedAt = null;
    }
}
