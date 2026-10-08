package com.t.tshow.domain.recommend.port;

import java.util.List;

/**
 * 검색 결과 후보 중에서 요청에 맞는 것을 고르고 이유를 붙이는 AI 창구. 실제 구현은 Gemini({@code infra/ai}), 테스트는 가짜 구현을 쓴다.
 * 후보는 번호(1부터)로만 주고받는다 — 긴 id 를 쓰면 토큰이 늘고 AI 가 없는 id 를 지어낼 수 있다.
 */
public interface AiCurator {

    /** AI 를 쓸 수 있는지 (API 키가 설정됐는지). false 면 호출하지 않는다 */
    boolean isConfigured();

    /**
     * @param request 사용자의 요청 문장과 적용된 조건을 풀어 쓴 글
     * @param today   오늘 날짜 글 (진행 중·예정 판단용)
     */
    Curation curate(String request, String today, List<Candidate> candidates);

    /** AI 에게 보여 줄 후보 한 건: 번호와 한 줄 설명 */
    record Candidate(int no, String line) {
    }

    /** AI 의 답: 한 줄 요약과 고른 후보(번호, 이유) */
    record Curation(String summary, List<Pick> picks) {
    }

    record Pick(int no, String reason) {
    }
}
