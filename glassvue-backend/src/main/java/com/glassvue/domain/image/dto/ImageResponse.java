package com.glassvue.domain.image.dto;

import com.glassvue.domain.image.entity.Image;
import java.util.UUID;

/**
 * 이미지 응답. {@code url}=원본, {@code mediumUrl}=상세용 800px, {@code thumbUrl}=목록용 200px.
 * 파생본이 없으면(생성 실패·구 이미지) medium/thumb는 원본으로 폴백해 항상 사용 가능한 URL을 준다
 * → 프론트는 목록=thumbUrl, 상세=mediumUrl, 클릭=url 로 쓰면 되고 null 처리가 필요 없다.
 */
public record ImageResponse(UUID id, String url, String mediumUrl, String thumbUrl) {

    public static ImageResponse from(Image i) {
        String original = i.getUrl();
        return new ImageResponse(
                i.getId(),
                original,
                i.getMediumUrl() != null ? i.getMediumUrl() : original,
                i.getThumbUrl() != null ? i.getThumbUrl() : original);
    }
}
