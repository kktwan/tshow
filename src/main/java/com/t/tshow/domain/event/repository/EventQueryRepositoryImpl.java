package com.t.tshow.domain.event.repository;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.ComparableExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.t.tshow.domain.event.dto.EventSearchCondition;
import com.t.tshow.domain.event.entity.Event;
import com.t.tshow.global.util.Geo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

import static com.t.tshow.domain.event.entity.QEvent.event;

public class EventQueryRepositoryImpl implements EventQueryRepository {

    private final JPAQueryFactory query;

    public EventQueryRepositoryImpl(JPAQueryFactory query) {
        this.query = query;
    }

    @Override
    public Page<Event> search(EventSearchCondition c, LocalDate today, Pageable pageable) {
        BooleanExpression[] where = {
                event.archivedAt.isNull(),
                kindsIn(c.kinds()),
                categoriesIn(c.categories()),
                sidoIs(c.sido()),
                sigunguIs(c.sigungu()),
                priceIs(c.priceType()),
                endsOnOrAfter(c.from()),
                startsOnOrBefore(c.to()),
                titleContains(c.keyword()),
                within(c.near())
        };
        List<Event> content = query.selectFrom(event)
                .where(where)
                .orderBy(soonest(today), endsTodayLast(today), withoutImageLast(), event.sourceCount.desc(),
                        event.endDate.asc().nullsLast(), event.title.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        Long total = query.select(event.count()).from(event).where(where).fetchOne();
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    /** 곧 시작하거나 지금 진행 중인 것부터: 이미 시작한 행사는 오늘 시작한 것으로 보고 정렬한다 */
    private static OrderSpecifier<LocalDate> soonest(LocalDate today) {
        ComparableExpression<LocalDate> effectiveStart = new CaseBuilder()
                .when(event.startDate.lt(today)).then(today)
                .otherwise(event.startDate);
        return new OrderSpecifier<>(Order.ASC, effectiveStart);
    }

    /** 오늘 끝나는 행사는 같은 시작일 안에서 뒤로 (지금 가려는 사람에게는 오늘 끝나는 것보다 더 많이 남은 것이 쓸모 있다) */
    private static OrderSpecifier<Integer> endsTodayLast(LocalDate today) {
        return new CaseBuilder().when(event.endDate.loe(today)).then(1).otherwise(0).asc();
    }

    /** 같은 날 시작하는 것 중에는 포스터가 있는 행사를 앞에 둔다 (화면이 더 보기 좋다). 그다음은 여러 소스에 올라 있는(더 알려진) 행사 순 */
    private static OrderSpecifier<Integer> withoutImageLast() {
        return new CaseBuilder().when(event.imageUrl.isNull()).then(1).otherwise(0).asc();
    }

    private static BooleanExpression kindsIn(List<String> kinds) {
        return kinds == null || kinds.isEmpty() ? null : event.kind.in(kinds);
    }

    private static BooleanExpression categoriesIn(List<String> categories) {
        return categories == null || categories.isEmpty() ? null : event.category.in(categories);
    }

    private static BooleanExpression sidoIs(String sido) {
        return StringUtils.hasText(sido) ? event.sidoCode.eq(sido) : null;
    }

    private static BooleanExpression sigunguIs(String sigungu) {
        return StringUtils.hasText(sigungu) ? event.sigunguCode.eq(sigungu) : null;
    }

    private static BooleanExpression priceIs(String priceType) {
        return StringUtils.hasText(priceType) ? event.priceType.eq(priceType) : null;
    }

    /** 이 날짜 이후에도 열리는 행사 (종료일이 없으면 시작일을 종료일로 본다) */
    private static BooleanExpression endsOnOrAfter(LocalDate from) {
        if (from == null) return null;
        return event.endDate.goe(from).or(event.endDate.isNull().and(event.startDate.goe(from)));
    }

    private static BooleanExpression startsOnOrBefore(LocalDate to) {
        return to == null ? null : event.startDate.loe(to);
    }

    private static BooleanExpression titleContains(String keyword) {
        return StringUtils.hasText(keyword) ? event.title.containsIgnoreCase(keyword.trim()) : null;
    }

    /** 반경을 감싸는 사각형 안의 행사 */
    private static BooleanExpression within(EventSearchCondition.Near near) {
        if (near == null) return null;
        double[] box = Geo.boundingBox(near.lat(), near.lon(), near.radiusMeters());
        return event.lat.between(box[0], box[1]).and(event.lon.between(box[2], box[3]));
    }
}
