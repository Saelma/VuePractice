package com.glassvue.domain.inquiry.service.command;

import com.glassvue.domain.catalog.service.query.ProductQueryService;
import com.glassvue.domain.image.service.ImageService;
import com.glassvue.domain.inquiry.dto.InquiryAnswerRequest;
import com.glassvue.domain.inquiry.dto.InquiryCreateRequest;
import com.glassvue.domain.inquiry.dto.InquiryUpdateRequest;
import com.glassvue.domain.inquiry.entity.Inquiry;
import com.glassvue.domain.inquiry.repository.InquiryRepository;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.security.AuthUser;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문의 조작(command) — 작성 · 수정 · 삭제 · (관리자)답변.
 * 상품 존재는 catalog 공개 서비스로 확인하고, 첨부 이미지는 image 공개 서비스로만 다룬다(도메인 경계).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class InquiryCommandService {

    private final InquiryRepository inquiryRepository;
    private final ProductQueryService productQueryService;
    private final ImageService imageService;

    public UUID create(UUID productId, InquiryCreateRequest req, AuthUser user) {
        productQueryService.ensureExists(productId);
        Inquiry inquiry = Inquiry.builder()
                .productId(productId)
                .authorId(user.id())
                .author(user.nickname())
                .title(req.title())
                .content(req.content())
                .secret(req.secret())
                .imageGroupId(imageService.createGroup(req.imageIds())) // 비면 null
                .build();
        Inquiry saved = inquiryRepository.save(inquiry);
        log.info("Inquiry created: id={} product={} by={}", saved.getId(), productId, user.id());
        return saved.getId();
    }

    /** 수정은 본인만, 답변 완료 전에만 가능. */
    public void update(UUID id, InquiryUpdateRequest req, AuthUser user) {
        Inquiry inquiry = findById(id);
        if (!inquiry.isOwnedBy(user.id())) {
            throw new BusinessException(ErrorCode.INQUIRY_NOT_OWNER);
        }
        if (inquiry.isAnswered()) {
            throw new BusinessException(ErrorCode.INQUIRY_ALREADY_ANSWERED);
        }
        UUID oldGroupId = inquiry.getImageGroupId();
        // 이미지는 새 그룹으로 통째 교체(Review.update와 동일) — 빈 목록이면 null이 되어 제거.
        inquiry.update(req.title(), req.content(), req.secret(), imageService.createGroup(req.imageIds()));
        // createGroup 뒤에 호출해야 유지할 이미지가 새 그룹으로 재할당된 뒤라 옛 그룹엔 뺀 이미지만 남는다.
        imageService.deleteGroup(oldGroupId);
    }

    /** 삭제는 본인 또는 관리자. */
    public void delete(UUID id, AuthUser user) {
        Inquiry inquiry = findById(id);
        boolean allowed = user.role() == Role.ADMIN || inquiry.isOwnedBy(user.id());
        if (!allowed) {
            throw new BusinessException(ErrorCode.INQUIRY_NOT_OWNER);
        }
        UUID imageGroupId = inquiry.getImageGroupId();
        inquiryRepository.delete(inquiry);
        imageService.deleteGroup(imageGroupId); // 문의가 사라지면 첨부 사진도 주인이 없다
    }

    /**
     * 답변 등록/수정 — 관리자 전용(권한은 SecurityConfig에서 강제).
     * 단, 본인이 등록한 문의에는 답변할 수 없다(질문자≠답변자).
     */
    public void answer(UUID id, InquiryAnswerRequest req, AuthUser user) {
        Inquiry inquiry = findById(id);
        if (inquiry.isOwnedBy(user.id())) {
            throw new BusinessException(ErrorCode.INQUIRY_SELF_ANSWER);
        }
        inquiry.answer(req.answer());
        log.info("Inquiry answered: id={} by={}", id, user.id());
    }

    private Inquiry findById(UUID id) {
        return inquiryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.INQUIRY_NOT_FOUND));
    }
}
