package com.t.tshow.domain.event.entity;

import com.t.tshow.domain.ingest.entity.SourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 삭제 요청 한 건. 제공처나 저작권자의 요청으로 특정 소스 레코드(또는 그 이미지)를 보이지 않게 한다.
 * 소스 레코드는 (source, sourceId)로 가리킨다 — 행사 id 는 병합을 다시 하면 바뀔 수 있지만 소스의 id 는 변하지 않는다.
 */
@Entity
@Table(name = "takedown")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Takedown {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TakedownKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SourceType source;

    @Column(name = "source_id", nullable = false, length = 100)
    private String sourceId;

    private String reason;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;
}
