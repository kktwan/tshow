package com.t.tshow.domain.event.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.t.tshow.domain.event.dto.EventSearchCondition;
import com.t.tshow.domain.event.entity.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.t.tshow.domain.event.entity.QEvent.event;

public class EventQueryRepositoryImpl implements EventQueryRepository {

    private final JPAQueryFactory query;

    public EventQueryRepositoryImpl(JPAQueryFactory query) {
        this.query = query;
    }

    @Override
    public Page<Event> search(EventSearchCondition c, Pageable pageable) {
        BooleanExpression[] where = {
                event.archivedAt.isNull(),
                categoryIs(c.category()),
                sidoIs(c.sido()),
                priceIs(c.priceType()),
                endsOnOrAfter(c),
                startsOnOrBefore(c),
                titleContains(c.keyword())
        };
        List<Event> content = query.selectFrom(event)
                .where(where)
                .orderBy(event.startDate.asc(), event.title.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        Long total = query.select(event.count()).from(event).where(where).fetchOne();
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private static BooleanExpression categoryIs(String category) {
        return StringUtils.hasText(category) ? event.category.eq(category) : null;
    }

    private static BooleanExpression sidoIs(String sido) {
        return StringUtils.hasText(sido) ? event.sidoCode.eq(sido) : null;
    }

    private static BooleanExpression priceIs(String priceType) {
        return StringUtils.hasText(priceType) ? event.priceType.eq(priceType) : null;
    }

    /** 조회 시작일 이후에도 열리는 행사 (종료일이 없으면 시작일을 종료일로 본다) */
    private static BooleanExpression endsOnOrAfter(EventSearchCondition c) {
        if (c.from() == null) return null;
        return event.endDate.goe(c.from()).or(event.endDate.isNull().and(event.startDate.goe(c.from())));
    }

    private static BooleanExpression startsOnOrBefore(EventSearchCondition c) {
        return c.to() == null ? null : event.startDate.loe(c.to());
    }

    private static BooleanExpression titleContains(String keyword) {
        return StringUtils.hasText(keyword) ? event.title.containsIgnoreCase(keyword.trim()) : null;
    }
}
