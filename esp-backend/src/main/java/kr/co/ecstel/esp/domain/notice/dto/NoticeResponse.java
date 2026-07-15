package kr.co.ecstel.esp.domain.notice.dto;

import java.time.Instant;
import java.util.UUID;
import kr.co.ecstel.esp.domain.notice.entity.Notice;

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
        return new NoticeResponse(
                n.getId(),
                n.getTitle(),
                n.getContent(),
                n.getAuthor(),
                n.getViewCount(),
                n.isPinned(),
                n.getCreatedAt(),
                n.getUpdatedAt()
        );
    }
}
