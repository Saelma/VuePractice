package com.glassvue.domain.notice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.util.UUID;
import com.glassvue.global.common.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Table(name = "notice")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice extends BaseTimeEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(nullable = false, length = 50)
    private String author; // 표시용 닉네임(작성 시점)

    // 소유권 판별용 회원 id. 로그인 이전에 만든 레거시 글은 null(소유자 없음).
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(columnDefinition = "RAW(16)")
    private UUID authorId;

    @Column(nullable = false)
    private long viewCount;

    @Column(nullable = false)
    private boolean pinned;

    @Builder
    private Notice(String title, String content, String author, UUID authorId, boolean pinned) {
        this.title = title;
        this.content = content;
        this.author = author;
        this.authorId = authorId;
        this.pinned = pinned;
        this.viewCount = 0L;
    }

    public void update(String title, String content, boolean pinned) {
        this.title = title;
        this.content = content;
        this.pinned = pinned;
    }
    // 조회수 증가는 Redis(NoticeViewCountStore) + 주기 플러시(NoticeRepository.addViewCount)로 처리한다.
}
