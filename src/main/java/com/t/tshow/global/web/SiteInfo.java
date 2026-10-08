package com.t.tshow.global.web;

/**
 * 모든 화면 하단에 보이는 사이트 정보.
 *
 * @param contactEmail 정정·삭제 요청 이메일 (설정이 없으면 빈 문자열)
 * @param updatedLabel 데이터를 마지막으로 갱신한 시각 글 (아직 수집한 적이 없으면 null)
 * @param year         저작권 표시의 해
 * @param baseUrl      서비스 주소 (끝의 / 없음)
 */
public record SiteInfo(String contactEmail, String updatedLabel, int year, String baseUrl) {
}
