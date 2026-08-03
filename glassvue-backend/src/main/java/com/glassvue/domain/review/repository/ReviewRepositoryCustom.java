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
}
