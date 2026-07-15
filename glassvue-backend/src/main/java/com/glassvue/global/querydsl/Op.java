package com.glassvue.global.querydsl;

/** @Cond 에서 쓰는 비교 연산자. */
public enum Op {
    EQ, NE, IN,
    CONTAINS, STARTS, ENDS,   // 문자열 (대소문자 무시)
    GOE, GT, LOE, LT          // 비교 (>=, >, <=, <)
}
