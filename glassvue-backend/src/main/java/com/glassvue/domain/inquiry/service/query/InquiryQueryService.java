package com.glassvue.domain.inquiry.service.query;

import com.glassvue.domain.catalog.dto.ProductResponse;
import com.glassvue.domain.catalog.service.query.ProductQueryService;
import com.glassvue.domain.image.dto.ImageResponse;
import com.glassvue.domain.image.service.ImageService;
import com.glassvue.domain.inquiry.dto.AdminInquiryResponse;
import com.glassvue.domain.inquiry.dto.InquiryResponse;
import com.glassvue.domain.inquiry.dto.MyInquiryResponse;
import com.glassvue.domain.inquiry.entity.Inquiry;
import com.glassvue.domain.inquiry.entity.InquiryStatus;
import com.glassvue.domain.inquiry.repository.InquiryRepository;
import com.glassvue.global.response.PageResponse;
import com.glassvue.global.security.AuthUser;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
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
    // catalog 의 **공개 조회 서비스**로만 부른다(리포지토리를 직접 만지지 않는다) — 관리자 리뷰와 같은 경로.
    private final ProductQueryService productQueryService;

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

    /**
     * 관리자 목록 (2026-08-06, G-3). status 가 null 이면 전체다.
     *
     * <p>🔴 <b>viewer 를 받지 않는다</b> — 경로가 이미 ADMIN 을 요구하고(SecurityConfig),
     * 관리자 목록은 마스킹 대상이 아니다({@link AdminInquiryResponse} 주석). viewer 를 받아 마스킹
     * 판단을 한 번 더 하면 <b>같은 규칙이 두 곳에</b> 생겨 한쪽만 고쳐진다.
     *
     * <p>상품명은 <b>한 번에 모아 읽는다</b>(N+1 회피 — 관리자 리뷰와 같은 방식).
     * ⚠ 이름을 못 찾은 문의는 <b>목록에서 빼지 않고</b> 이름만 빈다. 상품이 지워졌거나(문의는 느슨한
     * UUID 참조라 함께 안 지워진다) 애초에 상품 문의가 아니어도(2단계의 일반 문의) <b>답은 해야 한다.</b>
     */
    public PageResponse<AdminInquiryResponse> findForAdmin(InquiryStatus status, Pageable pageable) {
        Page<Inquiry> page = inquiryRepository.findForAdmin(status, pageable);

        List<UUID> productIds = page.getContent().stream()
                .map(Inquiry::getProductId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, String> nameById = productQueryService.findByIds(productIds).stream()
                .collect(Collectors.toMap(ProductResponse::id, ProductResponse::name));

        return PageResponse.from(page.map(i -> AdminInquiryResponse.from(
                i,
                i.getProductId() == null ? null : nameById.get(i.getProductId()))));
    }

    /**
     * 내 문의 목록 (2026-08-07, G-3 3단계) — 상품 문의·일반 문의를 <b>한 목록에</b> 준다.
     *
     * <p>🔴 <b>authorId 는 호출부가 넘긴 로그인 사용자여야 한다.</b> 경로 파라미터나 요청 본문에서
     * 받으면 남의 문의를 읽을 수 있다 — 그래서 컨트롤러가 {@code @LoginUser} 에서만 뽑는다.
     * 그 대신 여기엔 «내 것인가» 판정이 <b>없다</b>: 조회 조건 자체가 authorId 라 구조적으로 내 것뿐이다.
     *
     * <p>상품명과 이미지를 <b>각각 한 번에</b> 모아 읽는다(N+1 회피 — 위 두 메서드와 같은 방식).
     * ⚠ 상품명을 못 찾은 줄도 <b>빼지 않는다</b>: 일반 문의는 애초에 상품이 없고, 상품 문의인데 상품이
     * 지워졌을 수도 있다. 어느 쪽이든 <b>내가 물어본 것이고 답도 달려 있다</b> — 목록에서 사라지면
     * 알림을 눌러 온 사용자가 빈 화면을 본다.
     */
    public PageResponse<MyInquiryResponse> getMyInquiries(UUID authorId, Pageable pageable) {
        Page<Inquiry> page = inquiryRepository.findByAuthor(authorId, pageable);

        List<UUID> productIds = page.getContent().stream()
                .map(Inquiry::getProductId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, String> nameById = productQueryService.findByIds(productIds).stream()
                .collect(Collectors.toMap(ProductResponse::id, ProductResponse::name));

        List<UUID> groupIds = page.getContent().stream()
                .map(Inquiry::getImageGroupId).filter(Objects::nonNull).toList();
        Map<UUID, List<ImageResponse>> imagesByGroup = imageService.findByGroups(groupIds);

        return PageResponse.from(page.map(i -> MyInquiryResponse.from(
                i,
                i.getProductId() == null ? null : nameById.get(i.getProductId()),
                i.getImageGroupId() == null
                        ? List.of()
                        : imagesByGroup.getOrDefault(i.getImageGroupId(), List.of()))));
    }
}
