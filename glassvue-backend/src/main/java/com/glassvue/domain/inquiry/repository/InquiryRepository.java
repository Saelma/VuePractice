package com.glassvue.domain.inquiry.repository;

import com.glassvue.domain.inquiry.entity.Inquiry;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InquiryRepository extends JpaRepository<Inquiry, UUID>, InquiryRepositoryCustom {
}
