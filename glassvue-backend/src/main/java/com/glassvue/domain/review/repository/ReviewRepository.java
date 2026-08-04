package com.glassvue.domain.review.repository;

import com.glassvue.domain.review.dto.ReviewStats;
import com.glassvue.domain.review.entity.Review;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, UUID>, ReviewRepositoryCustom {

    /**
     * 한 회원이 한 상품에 이미 리뷰를 썼는지(상품당 1회 제한).
     *
     * <p>🔴 <b>숨긴 리뷰도 그대로 센다</b>(2026-08-04, B-18). 목록·집계와 <b>반대</b>인 유일한 자리다 —
     * 빼면 <b>숨기자마자 작성자가 새 리뷰를 쓸 수 있어 숨김이 무의미</b>해진다.
     * 그래서 여기엔 {@code hidden} 조건을 <b>넣지 않는다</b>(빠뜨린 것이 아니다).
     */
    boolean existsByProductIdAndAuthorId(UUID productId, UUID authorId);

    /**
     * 상품별 평균 별점·개수 집계. 리뷰가 없으면 average=null, count=0.
     *
     * <p>⚠ <b>숨긴 리뷰는 뺀다</b>(2026-08-04, B-18) — 안 빼면 화면에 보이지도 않는 리뷰가
     * 별점을 끌어내린다. 그래서 숨김·해제도 이 집계를 다시 내고 이벤트를 발행한다.
     */
    @Query("""
            select new com.glassvue.domain.review.dto.ReviewStats(avg(r.rating), count(r))
            from Review r where r.productId = :productId and r.hidden = false
            """)
    ReviewStats statsByProduct(@Param("productId") UUID productId);
}
