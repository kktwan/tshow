package com.t.tshow.domain.search.dto;

/** 화면의 선택 버튼 하나: 이름, 누르면 갈 주소, 지금 선택된 상태인지 */
public record ChipView(String label, String href, boolean active) {
}
