package kr.co.ecstel.esp.domain.notice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import kr.co.ecstel.esp.global.common.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
    private String author;

    @Column(nullable = false)
    private long viewCount;

    @Column(nullable = false)
    private boolean pinned;

    @Builder
    private Notice(String title, String content, String author, boolean pinned) {
        this.title = title;
        this.content = content;
        this.author = author;
        this.pinned = pinned;
        this.viewCount = 0L;
    }

    public void update(String title, String content, boolean pinned) {
        this.title = title;
        this.content = content;
        this.pinned = pinned;
    }

    /** 조회수 증가. (실제 카운팅은 이후 단계에서 Redis로 이관 예정) */
    public void increaseViewCount() {
        this.viewCount++;
    }
}
