package com.t.tshow.domain.event.service;

import com.t.tshow.domain.event.dto.EventDetail;
import com.t.tshow.domain.event.entity.Event;
import com.t.tshow.domain.event.repository.EventRepository;
import com.t.tshow.domain.ingest.dto.TicketLink;
import com.t.tshow.domain.ingest.entity.SourceType;
import com.t.tshow.domain.ingest.repository.SourceRecordRepository;
import com.t.tshow.domain.ingest.service.normalize.CategoryResolver;
import com.t.tshow.domain.ingest.service.normalize.RegionResolver;
import com.t.tshow.global.config.SearchProperties;
import com.t.tshow.global.exception.BusinessException;
import com.t.tshow.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 행사 상세 조회 */
@Service
@Transactional(readOnly = true)
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final EventRepository events;
    private final SourceRecordRepository sourceRecords;
    private final EventPresenter presenter;
    private final CategoryResolver categories;
    private final RegionResolver regions;
    private final SearchProperties properties;
    private final Clock clock;

    public EventService(EventRepository events, SourceRecordRepository sourceRecords, EventPresenter presenter,
                        CategoryResolver categories, RegionResolver regions, SearchProperties properties, Clock clock) {
        this.events = events;
        this.sourceRecords = sourceRecords;
        this.presenter = presenter;
        this.categories = categories;
        this.regions = regions;
        this.properties = properties;
        this.clock = clock;
    }

    public EventDetail get(UUID id) {
        Event e = events.findById(id)
                .filter(found -> found.getArchivedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        EventPresenter.Schedule schedule = presenter.schedule(e.getStartDate(), e.getEndDate(), LocalDate.now(clock));
        List<String> sources = sourceRecords.findSourcesOfEvent(id).stream().sorted().map(this::sourceName).toList();
        return new EventDetail(e.getId(), e.getKind(), categories.kindName(e.getKind()), e.getCategory(),
                categories.name(e.getCategory()), e.getTitle(), e.getDescription(), e.getStartDate(), e.getEndDate(),
                schedule.period(), schedule.status(), schedule.tone(), e.getVenueName(), e.getAddress(),
                regions.shortName(e.getSidoCode(), e.getSigunguCode()), e.getLat(), e.getLon(), e.getPriceType(),
                e.getPriceText(), e.getAgeText(), e.getRuntimeText(), e.getScheduleText(), e.getCastText(), e.getHostText(),
                e.getImageUrl(), e.getInfoUrl(), ticketLinks(e), sources);
    }

    private String sourceName(SourceType type) {
        return properties.sourceNames().getOrDefault(type.name(), type.name());
    }

    private static List<TicketLink> ticketLinks(Event e) {
        if (e.getTicketLinksJson() == null) return List.of();
        try {
            return JSON.readValue(e.getTicketLinksJson(), new TypeReference<List<TicketLink>>() { });
        } catch (RuntimeException ex) {
            log.warn("예매 링크를 읽지 못했어요 {}: {}", e.getId(), ex.getMessage());
            return List.of();
        }
    }
}
