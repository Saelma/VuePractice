package com.glassvue.domain.review.repository;

import com.glassvue.domain.review.entity.Review;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReviewRepositoryCustom {
    Page<Review> findByProduct(UUID productId, Pageable pageable);
}
