package com.t.tshow.infra.embedding;

import com.t.tshow.domain.index.port.Embedder;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/** Spring AI 의 OpenAI 임베딩 모델을 감싼다. 키가 없으면(자리표시 값이면) 사용 불가로 본다. */
@Component
public class SpringAiEmbedder implements Embedder {

    private final EmbeddingModel model;
    private final boolean configured;

    public SpringAiEmbedder(EmbeddingModel model, @Value("${spring.ai.openai.api-key:}") String apiKey) {
        this.model = model;
        this.configured = apiKey != null && !apiKey.isBlank() && !"not-configured".equals(apiKey);
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        return model.embed(texts);
    }
}
