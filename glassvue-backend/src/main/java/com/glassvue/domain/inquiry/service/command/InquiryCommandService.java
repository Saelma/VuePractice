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
import java.util.List;
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

    /**
     * 회원 삭제 정리(F-1) — 그 회원이 쓴 문의를 지운다(사용자 결정: 리뷰는 남기고 문의는 지운다 —
     * 문의는 본인↔관리자 대화라 내용에 개인정보가 들어갈 수 있고 비밀글도 있다).
     *
     * <p>⚠ <b>벌크 삭제로 하지 않았다.</b> 문의마다 첨부 이미지 그룹을 함께 해제해야 하는데
     * (단건 삭제 {@link #delete}가 지키는 규칙), 벌크 DELETE 는 그 경로를 건너뛰어
     * <b>주인 없는 이미지</b>를 남긴다 — 2026-07-20 에 실제로 고아 이미지 3건을 손으로 치운 그 문제다.
     */
    public void deleteAllForMember(UUID authorId) {
        List<Inquiry> inquiries = inquiryRepository.findByAuthorId(authorId);
        for (Inquiry inquiry : inquiries) {
            UUID imageGroupId = inquiry.getImageGroupId();
            inquiryRepository.delete(inquiry);
            imageService.deleteGroup(imageGroupId); // 문의가 사라지면 첨부 사진도 주인이 없다
        }
        log.info("Inquiries deleted for member {}: {}", authorId, inquiries.size());
    }
}
