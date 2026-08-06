package com.glassvue.domain.inquiry.repository;

import com.glassvue.domain.inquiry.entity.Inquiry;
import com.glassvue.domain.inquiry.entity.InquiryStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InquiryRepositoryCustom {
    Page<Inquiry> findByProduct(UUID productId, Pageable pageable);

    /**
     * 관리자 목록 — 상품을 <b>가로질러</b> 본다 (2026-08-06, G-3).
     *
     * <p>{@code status} 가 null 이면 전체다(관리자 리뷰의 {@code hidden} 과 같은 «세 번째 상태» 규약).
     * 기본값을 여기서 정하지 않는 이유: 기본을 «미답변» 으로 보이게 하는 것은 <b>화면의 판단</b>이고,
     * API 가 그걸 박아 두면 «전체» 를 볼 방법이 사라진다.
     */
    Page<Inquiry> findForAdmin(InquiryStatus status, Pageable pageable);
}
