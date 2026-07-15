package com.glassvue.domain.notice.dto;

import java.time.Instant;
import java.util.UUID;
import com.glassvue.domain.notice.entity.Notice;

public record NoticeResponse(
        UUID id,
        String title,
        String content,
        String author,
        UUID authorId,
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
                n.getAuthorId(),
                viewCount,
                n.isPinned(),
                n.getCreatedAt(),
                n.getUpdatedAt()
        );
    }
}
