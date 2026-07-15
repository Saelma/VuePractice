package com.glassvue.domain.notice.dto;

import com.glassvue.global.querydsl.Cond;
import com.glassvue.global.querydsl.Op;
import com.glassvue.global.querydsl.Transform;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 목록 검색 조건. 값이 없는 필드는 조건에서 제외된다(동적 검색).
 * @Cond 로 검색 방식을 선언하면 ConditionBuilder가 QueryDSL 조건을 자동 생성한다.
 */
public record NoticeSearchCondition(

        @Schema(description = "제목 검색어", example = "공지")
        @Cond(op = Op.CONTAINS)
        String title,

        @Schema(description = "작성자 검색어", example = "홍길동")
        @Cond(op = Op.CONTAINS)
        String author,

        @Schema(description = "작성일 시작(yyyy-MM-dd)", example = "2026-07-01")
        @Cond(path = "createdAt", op = Op.GOE, transform = Transform.DATE_START)
        LocalDate fromDate,

        @Schema(description = "작성일 종료(yyyy-MM-dd)", example = "2026-07-31")
        @Cond(path = "createdAt", op = Op.LT, transform = Transform.DATE_NEXT)
        LocalDate toDate
) {
}
