package com.glassvue.domain.inquiry.service.query;

import com.glassvue.domain.image.dto.ImageResponse;
import com.glassvue.domain.image.service.ImageService;
import com.glassvue.domain.inquiry.dto.InquiryResponse;
import com.glassvue.domain.inquiry.entity.Inquiry;
import com.glassvue.domain.inquiry.repository.InquiryRepository;
import com.glassvue.global.response.PageResponse;
import com.glassvue.global.security.AuthUser;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문의 조회(query) — 상품별 목록. 비밀글 마스킹은 viewer 기준으로 응답 DTO에서 처리.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InquiryQueryService {

    private final InquiryRepository inquiryRepository;
    private final ImageService imageService;

    /** viewer는 비로그인 시 null(비밀글 마스킹 판단용). */
    public PageResponse<InquiryResponse> getProductInquiries(UUID productId, Pageable pageable, AuthUser viewer) {
        Page<Inquiry> page = inquiryRepository.findByProduct(productId, pageable);

        // 페이지 문의들의 이미지 그룹을 한 번에 조회 (N+1 회피 — ReviewQueryService와 같은 방식).
        // 마스킹되는 비밀글 이미지도 여기선 담기지만, InquiryResponse.from이 마스킹 시 버린다.
        List<UUID> groupIds = page.getContent().stream()
                .map(Inquiry::getImageGroupId).filter(Objects::nonNull).toList();
        Map<UUID, List<ImageResponse>> imagesByGroup = imageService.findByGroups(groupIds);

        return PageResponse.from(page.map(i -> InquiryResponse.from(
                i,
                viewer,
                i.getImageGroupId() == null
                        ? List.of()
                        : imagesByGroup.getOrDefault(i.getImageGroupId(), List.of()))));
    }
}
