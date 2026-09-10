package com.glassvue.domain.notice.dto;

import java.time.Instant;
import java.util.UUID;
import com.glassvue.domain.notice.entity.Notice;

/**
 * 공지 응답.
 *
 * <p>🔴 <b>{@code authorId}(회원 UUID)를 싣지 않는다</b>(2026-09-10, R 축). 공지 목록·단건은
 * <b>비로그인도 부르는 공개 경로</b>인데 원시 UUIDv7 은 <b>관리자 계정의 가입 시각</b>까지 내보낸다.
 * 유일한 소비처였던 화면의 «본인 글만 수정» 가드는 <b>죽은 코드였다</b> — 라우트가 이미
 * {@code requiresAdmin} 이라 그 갈래에 닿지 못한다. 2026-08-20 에 서버({@code NoticeCommandService})
 * 에서 같은 이유로 지운 갈래가 <b>화면에만 남아 있었다</b>.
 */
public record NoticeResponse(
        UUID id,
        String title,
        String content,
        String author,
        long viewCount,
        boolean pinned,
        Instant createdAt,
        Instant updatedAt
) {
    public static NoticeResponse from(Notice n) {
        return from(n, n.getViewCount());
    }

    /** 조회수를 별도로 넘겨 생성 (DB 값 + Redis 미반영분 합산용) */
    public static NoticeResponse from(Notice n, long viewCount) {
        return new NoticeResponse(
                n.getId(),
                n.getTitle(),
                n.getContent(),
                n.getAuthor(),
                viewCount,
                n.isPinned(),
                n.getCreatedAt(),
                n.getUpdatedAt()
        );
    }
}
