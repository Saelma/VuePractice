package com.glassvue.domain.image.entity;

import com.glassvue.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "image")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Image extends BaseTimeEntity {

    // 업로드 직후엔 그룹이 없다(null). 도메인 저장 시 그룹에 연결된다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_group_id", columnDefinition = "RAW(16)")
    private ImageGroup imageGroup;

    @Column(nullable = false, length = 500)
    private String url; // 원본

    // 표시용 파생본(WebP). 생성 실패 시 null → 응답이 원본(url)으로 폴백.
    @Column(name = "medium_url", length = 500)
    private String mediumUrl; // 상세용 800px

    @Column(name = "thumb_url", length = 500)
    private String thumbUrl; // 목록용 200px

    @Column(length = 255)
    private String originalName;

    @Column(length = 100)
    private String contentType;

    @Column(name = "file_size") // size는 Oracle 예약어라 컬럼명 변경
    private long size;

    @Column
    private int sortOrder;

    @Builder
    private Image(String url, String mediumUrl, String thumbUrl,
                  String originalName, String contentType, long size) {
        this.url = url;
        this.mediumUrl = mediumUrl;
        this.thumbUrl = thumbUrl;
        this.originalName = originalName;
        this.contentType = contentType;
        this.size = size;
    }

    public void assignToGroup(ImageGroup group, int sortOrder) {
        this.imageGroup = group;
        this.sortOrder = sortOrder;
    }

    /** 파생본 백필 — 이미 값이 있으면 덮어쓰지 않는다(재실행해도 안전). */
    public void applyDerivatives(String mediumUrl, String thumbUrl) {
        if (this.mediumUrl == null && mediumUrl != null) {
            this.mediumUrl = mediumUrl;
        }
        if (this.thumbUrl == null && thumbUrl != null) {
            this.thumbUrl = thumbUrl;
        }
    }
}
