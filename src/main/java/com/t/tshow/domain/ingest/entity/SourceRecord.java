package com.t.tshow.domain.ingest.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 소스별로 정규화한 한 건 (source_record 테이블의 한 행). 분류·지역·날짜·가격이 표준 값으로 바뀌어 있다.
 * 중복 제거(병합)는 이 레코드들을 읽어 별도 단계에서 한다. id 는 저장된 뒤에만 있다(방금 정규화한 레코드는 null).
 * (source, sourceId) 가 같으면 같은 항목이다.
 */
@Entity
@Table(name = "source_record")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SourceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SourceType source;

    @Column(name = "source_id", nullable = false, length = 100)
    private String sourceId;

    @Column(nullable = false, length = 20)
    private String kind;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(name = "source_category", length = 100)
    private String sourceCategory;

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

    @Column(name = "source_updated_at")
    private OffsetDateTime sourceUpdatedAt;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    private String raw;

    /** 같은 항목을 다시 수집했을 때 내용을 새 값으로 바꾼다 (id·source·sourceId 는 그대로) */
    public void refreshFrom(SourceRecord n) {
        this.kind = n.kind;
        this.category = n.category;
        this.sourceCategory = n.sourceCategory;
        this.title = n.title;
        this.titleNorm = n.titleNorm;
        this.description = n.description;
        this.startDate = n.startDate;
        this.endDate = n.endDate;
        this.venueName = n.venueName;
        this.venueNameNorm = n.venueNameNorm;
        this.address = n.address;
        this.sidoCode = n.sidoCode;
        this.sigunguCode = n.sigunguCode;
        this.lat = n.lat;
        this.lon = n.lon;
        this.priceType = n.priceType;
        this.priceText = n.priceText;
        this.ageText = n.ageText;
        this.runtimeText = n.runtimeText;
        this.scheduleText = n.scheduleText;
        this.castText = n.castText;
        this.hostText = n.hostText;
        this.imageUrl = n.imageUrl;
        this.imageLicense = n.imageLicense;
        this.infoUrl = n.infoUrl;
        this.ticketLinksJson = n.ticketLinksJson;
        this.sourceUpdatedAt = n.sourceUpdatedAt;
        this.fetchedAt = n.fetchedAt;
        this.raw = n.raw;
    }
}
