package com.glassvue.global.querydsl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 검색 record 컴포넌트에 붙여, 어떤 필드를 어떤 연산으로 검색할지 선언한다.
 * ConditionBuilder가 이걸 읽어 QueryDSL 조건을 자동 생성한다.
 *
 * 예) @Cond(op = Op.CONTAINS) String title
 *     @Cond(path = "createdAt", op = Op.GOE, transform = Transform.DATE_START) LocalDate fromDate
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface Cond {

    /** Q 엔티티 필드 경로. 비우면 컴포넌트 이름을 사용. */
    String path() default "";

    Op op() default Op.EQ;

    Transform transform() default Transform.NONE;
}
