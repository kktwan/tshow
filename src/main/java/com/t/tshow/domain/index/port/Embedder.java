package com.t.tshow.domain.index.port;


import java.util.List;

/** 문장을 벡터로 바꾸는 창구. 실제 구현은 {@link SpringAiEmbedder}(OpenAI), 테스트는 가짜 벡터를 쓴다. */
public interface Embedder {

    /** 임베딩을 쓸 수 있는지 (API 키가 설정됐는지). false 면 색인 단계를 건너뛴다 */
    boolean isConfigured();

    /** texts 와 같은 순서로 벡터를 돌려준다 */
    List<float[]> embed(List<String> texts);
}
