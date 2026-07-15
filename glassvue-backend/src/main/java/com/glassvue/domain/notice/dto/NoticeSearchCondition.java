package com.glassvue.domain.notice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 목록 검색 조건. 값이 없는 필드는 조건에서 제외된다(동적 검색).
 */
public record NoticeSearchCondition(

        @Schema(description = "제목 검색어", example = "공지")
        String title,

        @Schema(description = "작성자 검색어", example = "홍길동")
        String author,

        @Schema(description = "작성일 시작(yyyy-MM-dd)", example = "2026-07-01")
        LocalDate fromDate,

        @Schema(description = "작성일 종료(yyyy-MM-dd)", example = "2026-07-31")
        LocalDate toDate
) {
}
