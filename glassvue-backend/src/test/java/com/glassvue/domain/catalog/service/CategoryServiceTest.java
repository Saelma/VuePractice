package com.glassvue.domain.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassvue.domain.audit.entity.AuditAction;
import com.glassvue.domain.audit.event.AdminActionEvent;
import com.glassvue.domain.catalog.dto.CategoryCreateRequest;
import com.glassvue.domain.catalog.dto.CategoryResponse;
import com.glassvue.domain.catalog.entity.Category;
import com.glassvue.domain.catalog.repository.CategoryRepository;
import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.security.AuthUser;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock CategoryRepository categoryRepository;
    @Mock ProductRepository productRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks CategoryService service;

    private final AuthUser admin = new AuthUser(UUID.randomUUID(), Role.ADMIN, "관리자");

    private AdminActionEvent capturedEvent() {
        ArgumentCaptor<AdminActionEvent> captor = ArgumentCaptor.forClass(AdminActionEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("생성: 이름 중복 → DUPLICATE_CATEGORY, 저장 안 함")
    void create_duplicate() {
        when(categoryRepository.existsByName("전자기기")).thenReturn(true);
        assertThatThrownBy(() -> service.create(new CategoryCreateRequest("전자기기"), admin))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_CATEGORY);
        verify(categoryRepository, never()).save(any());
        // ⚠ 막힌 요청은 원장에도 안 남는다 — 「일어난 일」만 적는다(2026-08-21, V56).
        verify(eventPublisher, never()).publishEvent(any(AdminActionEvent.class));
    }

    @Test
    @DisplayName("생성: 새 이름 → 저장 후 응답 반환")
    void create_ok() {
        when(categoryRepository.existsByName("패션")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));
        CategoryResponse res = service.create(new CategoryCreateRequest("패션"), admin);
        assertThat(res.name()).isEqualTo("패션");
    }

    @Test
    @DisplayName("생성: 원장에 CATEGORY_CREATE 를 남긴다 — detail 은 카테고리명 (2026-08-21, V56)")
    void create_audit() {
        when(categoryRepository.existsByName("패션")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));
        service.create(new CategoryCreateRequest("패션"), admin);

        AdminActionEvent event = capturedEvent();
        assertThat(event.action()).isEqualTo(AuditAction.CATEGORY_CREATE);
        assertThat(event.actorId()).isEqualTo(admin.id());
        assertThat(event.detail()).isEqualTo("패션");
        // 🔴 대상이 회원이 아니다 — targetLogin 이 비어 있어 「대상 아이디」로는 못 찾는다.
        //    그래서 target_type(CATEGORY)이 필요했다(V56 이 그 값을 세운 이유).
        assertThat(event.targetLogin()).isNull();
    }

    @Test
    @DisplayName("삭제: 없는 카테고리 → CATEGORY_NOT_FOUND")
    void delete_notFound() {
        UUID id = UUID.randomUUID();
        when(categoryRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(id, admin))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("삭제: 소속 상품 있음 → CATEGORY_IN_USE, 삭제 안 함")
    void delete_inUse() {
        UUID id = UUID.randomUUID();
        when(categoryRepository.findById(id)).thenReturn(Optional.of(Category.builder().name("패션").build()));
        when(productRepository.existsByCategoryId(id)).thenReturn(true);
        assertThatThrownBy(() -> service.delete(id, admin))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CATEGORY_IN_USE);
        verify(categoryRepository, never()).delete(any());
        verify(eventPublisher, never()).publishEvent(any(AdminActionEvent.class));
    }

    @Test
    @DisplayName("삭제: 빈 카테고리 → 삭제")
    void delete_ok() {
        UUID id = UUID.randomUUID();
        Category category = Category.builder().name("패션").build();
        when(categoryRepository.findById(id)).thenReturn(Optional.of(category));
        when(productRepository.existsByCategoryId(id)).thenReturn(false);
        service.delete(id, admin);
        verify(categoryRepository).delete(category);
    }

    @Test
    @DisplayName("🔴 삭제: 이름을 **지우기 전에** 읽어 원장에 남긴다 — 카테고리엔 유예가 없다 (V56)")
    void delete_audit() {
        UUID id = UUID.randomUUID();
        Category category = Category.builder().name("없어질분류").build();
        when(categoryRepository.findById(id)).thenReturn(Optional.of(category));
        when(productRepository.existsByCategoryId(id)).thenReturn(false);
        service.delete(id, admin);

        AdminActionEvent event = capturedEvent();
        assertThat(event.action()).isEqualTo(AuditAction.CATEGORY_DELETE);
        // 🔴 행이 진짜로 사라지므로 이 한 줄이 **유일하게 남는 흔적**이다.
        //    지운 뒤에 읽는 구현이었다면 여기가 빈칸이 된다.
        assertThat(event.detail()).isEqualTo("없어질분류");
        assertThat(event.targetId()).isEqualTo(id);
    }
}
