package com.glassvue.domain.catalog.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassvue.domain.catalog.dto.ProductResponse;
import com.glassvue.domain.catalog.entity.Category;
import com.glassvue.domain.catalog.entity.Product;
import com.glassvue.domain.catalog.entity.ProductStatus;
import com.glassvue.domain.catalog.repository.CategoryRepository;
import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.domain.image.service.ImageService;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * ProductQueryService 통합 — 실 DB. get()이 상품 + 카테고리(연관) + 이미지(합성)를 조립하는지,
 * ensureExists/findByIds 동작 검증. @Transactional 롤백.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@Transactional
class ProductQueryServiceIntegrationTest {

    @Autowired ProductQueryService queryService;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ImageService imageService;

    private UUID productId;

    @BeforeEach
    void setUp() {
        Category cat = categoryRepository.save(Category.builder().name("ZZC-쿼리").build());
        UUID groupId = imageService.createGroup(List.of()); // 이미지 없는 빈 그룹
        Product p = productRepository.save(Product.builder()
                .name("ZZP-쿼리상품").description("d").price(12_345).stock(7)
                .status(ProductStatus.SELLING).imageGroupId(groupId).category(cat).build());
        productId = p.getId();
    }

    @Test
    @DisplayName("get: 상품 + 카테고리명 합성, 이미지 없으면 빈 리스트")
    void get_composed() {
        ProductResponse r = queryService.get(productId);
        assertThat(r.name()).isEqualTo("ZZP-쿼리상품");
        assertThat(r.price()).isEqualTo(12_345);
        assertThat(r.categoryName()).isEqualTo("ZZC-쿼리");
        assertThat(r.images()).isEmpty();
    }

    @Test
    @DisplayName("get: 없는 상품 → PRODUCT_NOT_FOUND")
    void get_notFound() {
        assertThatThrownBy(() -> queryService.get(UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("ensureExists: 있으면 통과, 없으면 PRODUCT_NOT_FOUND")
    void ensureExists() {
        assertThatCode(() -> queryService.ensureExists(productId)).doesNotThrowAnyException();
        assertThatThrownBy(() -> queryService.ensureExists(UUID.randomUUID()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("findByIds: 매칭 상품 반환, 빈 입력은 빈 리스트")
    void findByIds() {
        assertThat(queryService.findByIds(List.of(productId))).hasSize(1);
        assertThat(queryService.findByIds(List.of())).isEmpty();
    }
}
