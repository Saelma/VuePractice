package com.glassvue.domain.inquiry.service.query;

import com.glassvue.domain.inquiry.dto.InquiryResponse;
import com.glassvue.domain.inquiry.entity.Inquiry;
import com.glassvue.domain.inquiry.repository.InquiryRepository;
import com.glassvue.global.response.PageResponse;
import com.glassvue.global.security.AuthUser;
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

    /** viewer는 비로그인 시 null(비밀글 마스킹 판단용). */
    public PageResponse<InquiryResponse> getProductInquiries(UUID productId, Pageable pageable, AuthUser viewer) {
        Page<Inquiry> page = inquiryRepository.findByProduct(productId, pageable);
        return PageResponse.from(page.map(i -> InquiryResponse.from(i, viewer)));
    }
}
