package com.glassvue.domain.review.repository;

import com.glassvue.domain.review.entity.Review;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReviewRepositoryCustom {
    /**
     * 상품별 리뷰 목록.
     *
     * @param photoOnly {@code true} 면 <b>사진이 있는 리뷰만</b>(B-22, 2026-08-03).
     *                  ⚠ 정렬은 {@code pageable} 이 담당한다 — 화이트리스트는 구현체에 있다.
     */
    Page<Review> findByProduct(UUID productId, boolean photoOnly, Pageable pageable);

    /**
     * 관리자 리뷰 목록 (2026-08-04, B-18) — <b>상품을 가로질러</b> 전체를 본다.
     *
     * <p>{@link #findByProduct} 와 나눈 이유: 저건 고객 화면이라 <b>숨긴 것을 빼는 게 규칙</b>이고,
     * 여기는 <b>숨긴 것을 봐야 되돌릴 수 있다</b>. 한 메서드에 «누구에게 보이나» 플래그를 더하면
     * 그 규칙이 호출부마다 갈린다 — 조건이 반대인 조회는 자리를 나눈다.
     *
     * @param hidden {@code null} 이면 전체, 아니면 그 상태만(숨김만/보이는 것만).
     */
    Page<Review> findForAdmin(Boolean hidden, Pageable pageable);
}
