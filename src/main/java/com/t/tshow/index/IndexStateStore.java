package com.t.tshow.index;

import com.t.tshow.event.Event;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 색인 상태를 저장하는 곳(event 테이블). 실제 구현은 {@link JdbcIndexStateStore}, 테스트는 메모리 구현을 쓴다.
 * 행사마다 "어떤 임베딩 텍스트·payload 로 색인했는지"를 해시로 기억해서 바뀐 것만 다시 올린다.
 */
public interface IndexStateStore {

    /** 보관 기간이 지나지 않은(논리삭제되지 않은) 행사와 그 색인 상태 */
    List<Stored> activeEvents();

    /** 논리삭제됐는데 아직 색인에 올라가 있는 행사 id (색인에서 지울 대상) */
    List<UUID> archivedStillIndexed();

    /** 색인에 올렸음을 기록한다 */
    void markIndexed(UUID id, String embedHash, String payloadHash);

    /** 색인에서 지웠음을 기록한다 */
    void clearIndexed(Collection<UUID> ids);

    /**
     * 행사와 마지막으로 색인한 상태. indexed 가 false 면 색인에 없는 행사다.
     */
    record Stored(Event event, String embedHash, String payloadHash, boolean indexed) {
    }
}
