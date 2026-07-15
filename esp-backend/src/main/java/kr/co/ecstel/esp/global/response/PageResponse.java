package kr.co.ecstel.esp.global.response;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 목록 응답. Spring Data Page를 프론트 친화적인 형태로 감싼다.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
