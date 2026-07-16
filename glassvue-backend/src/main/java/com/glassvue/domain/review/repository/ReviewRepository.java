package com.glassvue.domain.review.repository;

import com.glassvue.domain.review.dto.ReviewStats;
import com.glassvue.domain.review.entity.Review;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, UUID>, ReviewRepositoryCustom {

    /** 한 회원이 한 상품에 이미 리뷰를 썼는지(상품당 1회 제한). */
    boolean existsByProductIdAndAuthorId(UUID productId, UUID authorId);

    /** 상품별 평균 별점·개수 집계. 리뷰가 없으면 average=null, count=0. */
    @Query("""
            select new com.glassvue.domain.review.dto.ReviewStats(avg(r.rating), count(r))
            from Review r where r.productId = :productId
            """)
    ReviewStats statsByProduct(@Param("productId") UUID productId);
}
