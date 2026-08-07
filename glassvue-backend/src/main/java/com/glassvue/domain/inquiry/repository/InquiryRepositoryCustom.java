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

    /**
     * 내 문의 목록 — 상품 문의와 일반 문의를 <b>함께</b> 본다 (2026-08-07, G-3 3단계).
     *
     * <p>⚠ <b>가르지 않는 것이 요점이다.</b> 사용자가 «내가 물어본 것» 을 찾을 때 그게 상품에 달렸는지
     * 고객센터에 냈는지는 기억나지 않는다 — 나누면 두 목록을 번갈아 봐야 한다(관리자 쪽에서 1단계가
     * 방금 없앤 문제와 같다). 유형은 <b>줄마다 배지로</b> 보여 준다.
     *
     * <p>⚠ {@link InquiryRepository#findByAuthorId} 와 다르다 — 저쪽은 <b>탈퇴 정리(F-1) 전용</b>이라
     * 페이징이 없고 전건을 메모리로 올린다. 화면용으로 쓰면 문의가 쌓인 회원에서 터진다.
     */
    Page<Inquiry> findByAuthor(UUID authorId, Pageable pageable);
}
