package com.t.tshow.domain.recommend.dto;

import com.t.tshow.domain.search.dto.EventCard;

/** AI 가 고른 행사 하나. reason 은 AI 가 쓴 한 줄 이유 (AI 를 못 썼을 때는 null) */
public record AiPick(EventCard card, String reason) {
}
