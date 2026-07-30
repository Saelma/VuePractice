package com.glassvue.domain.inquiry.repository;

import com.glassvue.domain.inquiry.entity.Inquiry;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InquiryRepository extends JpaRepository<Inquiry, UUID>, InquiryRepositoryCustom {

    /**
     * 회원 삭제 정리용(F-1). ⚠ <b>벌크 삭제가 아니라 목록으로 받는다</b> — 문의마다 첨부 이미지 그룹을
     * 함께 해제해야 하므로(단건 삭제 경로와 같은 규칙), 지울 대상을 알아야 한다.
     */
    java.util.List<Inquiry> findByAuthorId(UUID authorId);
}
