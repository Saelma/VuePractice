package com.glassvue.domain.inquiry.service.command;

import com.glassvue.domain.catalog.service.query.ProductQueryService;
import com.glassvue.domain.image.service.ImageService;
import com.glassvue.domain.inquiry.dto.GeneralInquiryCreateRequest;
import com.glassvue.domain.inquiry.dto.InquiryAnswerRequest;
import com.glassvue.domain.inquiry.dto.InquiryCreateRequest;
import com.glassvue.domain.inquiry.dto.InquiryUpdateRequest;
import com.glassvue.domain.inquiry.entity.Inquiry;
import com.glassvue.domain.inquiry.entity.InquiryType;
import com.glassvue.domain.inquiry.event.InquiryAnsweredEvent;
import com.glassvue.domain.inquiry.repository.InquiryRepository;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.security.AuthUser;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

    /** 상품 문의 — 유형은 <b>경로가 정한다</b>(요청 본문에 type 이 없는 이유). */
    public UUID create(UUID productId, InquiryCreateRequest req, AuthUser user) {
        productQueryService.ensureExists(productId);
        Inquiry inquiry = Inquiry.builder()
                .productId(productId)
                .type(InquiryType.PRODUCT)
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

    /**
     * 일반 고객센터 문의 (2026-08-07, G-3 2단계) — 상품이 없다.
     *
     * <p>이 메서드가 생기기 전까지 문의 작성 경로는 {@code POST /products/{id}/inquiries} <b>하나</b>였다.
     * 즉 *"배송이 안 와요"* 를 물으려면 <b>아무 상품이나 골라야</b> 했고, 그러면 그 문의는 그 상품의
     * 문의 목록에 남의 일처럼 걸린다.
     *
     * <p>⚠ <b>PRODUCT 는 거부한다.</b> 조용히 다른 값으로 바꾸지 않는다 — 그러면 사용자가 고른 유형이
     * 사라진 채 200 이 나가고, 관리자 목록에는 <b>상품명이 없는 상품 문의</b> 라는 앞뒤 안 맞는 줄이 뜬다.
     * ({@code Inquiry} 생성자와 DB 제약도 같은 규칙을 걸지만, 여기서 잡아야 사용자에게 <b>왜</b> 를 말해 준다.)
     */
    public UUID createGeneral(GeneralInquiryCreateRequest req, AuthUser user) {
        if (req.type().requiresProduct()) {
            throw new BusinessException(ErrorCode.INQUIRY_TYPE_NOT_GENERAL);
        }
        Inquiry inquiry = Inquiry.builder()
                .productId(null) // 일반 문의라 상품이 없다 — 명시해 둔다(빠뜨린 게 아니다)
                .type(req.type())
                .authorId(user.id())
                .author(user.nickname())
                .title(req.title())
                .content(req.content())
                .secret(req.secret())
                .imageGroupId(imageService.createGroup(req.imageIds())) // 비면 null
                .build();
        Inquiry saved = inquiryRepository.save(inquiry);
        log.info("General inquiry created: id={} type={} by={}", saved.getId(), req.type(), user.id());
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
        // ⚠ 상태를 **바꾸기 전에** 읽는다. answer() 가 ANSWERED 로 올려 버리므로 뒤에서 보면 항상 true 다.
        boolean firstAnswer = !inquiry.isAnswered();
        inquiry.answer(req.answer());
        log.info("Inquiry answered: id={} by={} first={}", id, user.id(), firstAnswer);
        if (firstAnswer) {
            // 처음 답이 달렸을 때만 알린다(B-15) — 수정마다 보내면 오타 고칠 때마다 알림이 간다.
            // 알림 생성은 notification 도메인이 이벤트를 받아서 한다(도메인 간 직접 참조 금지).
            eventPublisher.publishEvent(new InquiryAnsweredEvent(
                    inquiry.getId(), inquiry.getProductId(), inquiry.getAuthorId(), inquiry.getTitle()));
        }
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
