package com.glassvue.domain.catalog.service;

import com.glassvue.domain.audit.entity.AuditAction;
import com.glassvue.domain.audit.event.AdminActionEvent;
import com.glassvue.domain.catalog.dto.CategoryCreateRequest;
import com.glassvue.domain.catalog.dto.CategoryResponse;
import com.glassvue.domain.catalog.entity.Category;
import com.glassvue.domain.catalog.repository.CategoryRepository;
import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.security.AuthUser;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 카테고리 등록. 관리자 조작이라 원장에 남긴다 (2026-08-21, V56 — 감사 확대 4차).
     *
     * <p>⚠ <b>중복 이름으로 막힌 요청은 안 남는다</b> — 예외가 나가면 트랜잭션이 롤백되고 이벤트도
     * 함께 사라진다(감사와 조작이 같은 트랜잭션). 「일어난 일」만 원장에 있다는 뜻이고,
     * V44 가 «실제로 바뀔 때만 남긴다» 로 정한 것과 같은 결이다.
     */
    public CategoryResponse create(CategoryCreateRequest req, AuthUser actor) {
        if (categoryRepository.existsByName(req.name())) {
            throw new BusinessException(ErrorCode.DUPLICATE_CATEGORY);
        }
        Category category = categoryRepository.save(Category.builder().name(req.name()).build());
        publishAudit(AuditAction.CATEGORY_CREATE, actor, category.getId(), category.getName());
        return CategoryResponse.from(category);
    }

    /**
     * 카테고리 삭제. 소속 상품이 하나라도 있으면 막는다(CATEGORY_IN_USE).
     * product.category_id가 nullable=false FK라 상품이 있으면 어차피 DB가 막지만,
     * 여기서 먼저 걸러 의미 있는 409를 돌려준다(FK 위반 500 대신).
     *
     * <p>🔴 <b>되돌릴 수 없다</b> — 카테고리에는 유예(F-7)가 없다. 같은 이름으로 다시 만들 수는 있어도
     * <b>id 가 달라져</b> 예전 것과 같은 것이 아니다. 그래서 원장에 남긴다(2026-08-21, V56).
     */
    public void delete(UUID id, AuthUser actor) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        if (productRepository.existsByCategoryId(id)) {
            throw new BusinessException(ErrorCode.CATEGORY_IN_USE);
        }
        // ⚠ 지우기 **전에** 이름을 읽는다 — 지운 뒤엔 «무엇을 지웠나» 를 적을 값이 없다
        //    (DISCOUNT_DELETE·PRODUCT_DELETE 와 같은 자리). 🔴 카테고리에는 soft delete 가 없어
        //    행이 진짜로 사라지므로, 이 detail 이 **유일하게 남는 흔적**이다.
        String name = category.getName();
        categoryRepository.delete(category);
        publishAudit(AuditAction.CATEGORY_DELETE, actor, id, name);
    }

    /**
     * ⚠ audit 의 내부를 직접 부르지 않고 이벤트만 발행한다(도메인 간 직접 참조 금지 — CLAUDE.md).
     * 기본 {@code @EventListener} 라 <b>같은 트랜잭션</b>이다 — 감사가 실패하면 조작도 롤백된다.
     *
     * <p>⚠ {@code targetLogin} 은 {@code null} 이다 — 대상이 회원이 아니다. 대신 {@code target_type}
     * 이 {@code CATEGORY} 라 「대상 종류」 필터로 걸린다(V56 이 그 값을 세운 이유다).
     */
    private void publishAudit(AuditAction action, AuthUser actor, UUID categoryId, String name) {
        eventPublisher.publishEvent(new AdminActionEvent(
                action, actor.id(), actor.nickname(), categoryId, null, name));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(CategoryResponse::from)
                .toList();
    }
}
