package com.t.tshow.domain.event.service.merge;

import com.t.tshow.domain.event.entity.Takedown;
import com.t.tshow.domain.event.entity.TakedownKind;
import com.t.tshow.domain.ingest.entity.SourceRecord;
import com.t.tshow.domain.ingest.entity.SourceType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 삭제 요청 목록(takedown)을 병합 입력에 적용한다. DB 를 모르는 순수 계산이라 단위 테스트로 검증한다.
 * 병합이 항상 먼저 적용하므로 수집이 매일 다시 돌아도 요청받은 내용이 되살아나지 않는다.
 *
 * <ul>
 *   <li>RECORD: 그 소스 레코드를 병합에서 뺀다. 같은 행사의 다른 소스 레코드는 그대로 쓰이고, 남은 것이 없으면 행사가 사라진다</li>
 *   <li>IMAGE: 그 소스 레코드의 이미지(와 이용 조건)만 뺀다. 다른 소스의 이미지가 있으면 그것을 쓴다</li>
 * </ul>
 */
@Component
public class TakedownFilter {

    /** 적용 결과: 병합에 쓸 레코드와 각 요청이 실제로 적용된 건수 */
    public record Result(List<SourceRecord> records, int excluded, int imagesRemoved) {
    }

    private record Key(SourceType source, String sourceId) {
    }

    public Result apply(List<SourceRecord> records, List<Takedown> takedowns) {
        Set<Key> hidden = new HashSet<>();
        Set<Key> noImage = new HashSet<>();
        for (Takedown t : takedowns) {
            (t.getKind() == TakedownKind.RECORD ? hidden : noImage).add(new Key(t.getSource(), t.getSourceId()));
        }
        if (hidden.isEmpty() && noImage.isEmpty()) return new Result(records, 0, 0);

        List<SourceRecord> kept = new ArrayList<>(records.size());
        int excluded = 0;
        int imagesRemoved = 0;
        for (SourceRecord r : records) {
            Key key = new Key(r.getSource(), r.getSourceId());
            if (hidden.contains(key)) {
                excluded++;
                continue;
            }
            if (noImage.contains(key) && r.getImageUrl() != null) {
                kept.add(r.toBuilder().imageUrl(null).imageLicense(null).build());
                imagesRemoved++;
            } else {
                kept.add(r);
            }
        }
        return new Result(kept, excluded, imagesRemoved);
    }
}
