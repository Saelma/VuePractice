package com.glassvue.domain.catalog.dto;

import com.glassvue.domain.catalog.entity.Category;
import java.util.UUID;

public record CategoryResponse(UUID id, String name) {

    public static CategoryResponse from(Category c) {
        return new CategoryResponse(c.getId(), c.getName());
    }
}
