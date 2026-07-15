package com.glassvue.domain.image.dto;

import com.glassvue.domain.image.entity.Image;
import java.util.UUID;

public record ImageResponse(UUID id, String url) {

    public static ImageResponse from(Image i) {
        return new ImageResponse(i.getId(), i.getUrl());
    }
}
