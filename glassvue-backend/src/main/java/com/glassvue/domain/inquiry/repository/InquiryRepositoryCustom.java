package com.glassvue.domain.inquiry.repository;

import com.glassvue.domain.inquiry.entity.Inquiry;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InquiryRepositoryCustom {
    Page<Inquiry> findByProduct(UUID productId, Pageable pageable);
}
