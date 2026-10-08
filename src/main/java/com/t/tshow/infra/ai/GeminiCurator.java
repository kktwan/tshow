package com.t.tshow.infra.ai;

import com.t.tshow.domain.recommend.port.AiCurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Spring AI 의 Gemini 채팅 모델로 후보를 고르게 한다. 지시문은 리소스(prompts/event-curator.st)에 있고,
 * 키가 없으면(자리표시 값이면) 사용 불가로 본다.
 */
@Component
public class GeminiCurator implements AiCurator {

    private static final Logger log = LoggerFactory.getLogger(GeminiCurator.class);
    private static final String PROMPT = "prompts/event-curator.st";

    /** 모델이 돌려주는 JSON. id 는 후보 번호이지만 모델이 문자열로 줄 수 있어 문자열로 받는다 */
    record ModelAnswer(String summary, List<ModelPick> picks) {
    }

    record ModelPick(String id, String reason) {
    }

    private final ChatClient chat;
    private final boolean configured;
    private final String systemPrompt;

    public GeminiCurator(ChatClient.Builder builder, @Value("${spring.ai.google.genai.api-key:}") String apiKey) {
        this.chat = builder.build();
        this.configured = apiKey != null && !apiKey.isBlank() && !"not-configured".equals(apiKey);
        try {
            this.systemPrompt = new ClassPathResource(PROMPT).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(PROMPT + " 를 읽지 못했어요", e);
        }
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public Curation curate(String request, String today, List<Candidate> candidates) {
        StringBuilder user = new StringBuilder();
        user.append("오늘: ").append(today).append('\n');
        user.append("요청: ").append(request).append("\n\n[후보 행사]\n");
        candidates.forEach(c -> user.append(c.no()).append(" | ").append(c.line()).append('\n'));
        log.debug("AI 추천 요청: {}", user);

        ModelAnswer answer = chat.prompt().system(systemPrompt).user(user.toString()).call().entity(ModelAnswer.class);
        log.debug("AI 추천 응답: {}", answer);

        List<Pick> picks = new ArrayList<>();
        if (answer != null && answer.picks() != null) {
            for (ModelPick p : answer.picks()) {
                if (p == null || p.id() == null) continue;
                try {
                    picks.add(new Pick(Integer.parseInt(p.id().trim()), p.reason() == null ? "" : p.reason().trim()));
                } catch (NumberFormatException e) {
                    log.debug("후보 번호가 아닌 id 는 무시해요: {}", p.id());
                }
            }
        }
        return new Curation(answer == null || answer.summary() == null ? "" : answer.summary().trim(), picks);
    }
}
