package com.t.tshow.domain.event.entity;

/** 삭제 요청의 종류 */
public enum TakedownKind {
    /** 소스 레코드를 병합에서 제외한다 (행사에서 이 소스의 모든 내용이 빠진다) */
    RECORD,
    /** 소스 레코드의 이미지만 쓰지 않는다 */
    IMAGE
}
