package com.glassvue.domain.inquiry.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassvue.domain.inquiry.entity.Inquiry;
import com.glassvue.domain.inquiry.entity.InquiryType;
import com.glassvue.global.config.JpaAuditingConfig;
import com.glassvue.global.config.QuerydslConfig;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * InquiryRepository QueryDSL 통합 — 실 Oracle. 상품별 목록(findByProduct) + 정렬 화이트리스트.
 * 문의는 느슨한 UUID 참조라 실제 상품 없이 랜덤 productId로 격리.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QuerydslConfig.class, JpaAuditingConfig.class})
class InquiryRepositoryIntegrationTest {

    @Autowired InquiryRepository inquiryRepository;

    private final UUID productId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        save(productId, "배송 문의", false);
        save(productId, "재고 문의", false);
        Inquiry answered = save(productId, "환불 문의", true);
        answered.answer("환불 처리해드립니다"); // 상태 ANSWERED
        inquiryRepository.save(answered);
        save(UUID.randomUUID(), "다른 상품 문의", false); // 다른 상품
    }

    private Inquiry save(UUID pid, String title, boolean secret) {
        return inquiryRepository.save(Inquiry.builder()
                .productId(pid).type(InquiryType.PRODUCT).authorId(UUID.randomUUID()).author("nick")
                .title(title).content("c").secret(secret).build());
    }

    private PageRequest page(Sort sort) {
        return PageRequest.of(0, 10, sort);
    }

    @Test
    @DisplayName("상품별 목록 — 해당 상품 3건만")
    void findByProduct() {
        var result = inquiryRepository.findByProduct(productId, page(Sort.unsorted()));
        assertThat(result.getContent()).hasSize(3)
                .allSatisfy(i -> assertThat(i.getProductId()).isEqualTo(productId));
    }

    @Test
    @DisplayName("허용된 정렬(status)은 정상")
    void sortAllowed() {
        var result = inquiryRepository.findByProduct(productId, page(Sort.by("status").descending()));
        assertThat(result.getContent()).hasSize(3);
    }

    @Test
    @DisplayName("화이트리스트 밖 정렬(title) → INVALID_INPUT")
    void sortNotAllowed() {
        assertThatThrownBy(() -> inquiryRepository.findByProduct(productId, page(Sort.by("title"))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }
}
