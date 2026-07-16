package com.glassvue.domain.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.glassvue.domain.catalog.dto.ProductSearchCondition;
import com.glassvue.domain.catalog.entity.Category;
import com.glassvue.domain.catalog.entity.Product;
import com.glassvue.domain.catalog.entity.ProductStatus;
import com.glassvue.global.config.JpaAuditingConfig;
import com.glassvue.global.config.QuerydslConfig;
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
 * ProductRepository QueryDSL 통합 — 실 Oracle. @Cond 동적검색(이름/가격/상태) + 카테고리 연관 필터(탈출구).
 * 기존 상품과 섞이지 않게 이름 표식 ZZP 로 스코프.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QuerydslConfig.class, JpaAuditingConfig.class})
class ProductRepositoryIntegrationTest {

    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;

    private static final String MARK = "ZZP";
    private UUID catElecId;
    private UUID catFashionId;

    @BeforeEach
    void setUp() {
        Category elec = categoryRepository.save(Category.builder().name("ZZC-전자").build());
        Category fashion = categoryRepository.save(Category.builder().name("ZZC-패션").build());
        catElecId = elec.getId();
        catFashionId = fashion.getId();
        save(MARK + "-키보드", 30_000, ProductStatus.SELLING, elec);
        save(MARK + "-마우스", 15_000, ProductStatus.SELLING, elec);
        save(MARK + "-티셔츠", 20_000, ProductStatus.SOLD_OUT, fashion);
    }

    private void save(String name, long price, ProductStatus status, Category category) {
        productRepository.save(Product.builder()
                .name(name).description("d").price(price).stock(10).status(status).category(category).build());
    }

    private ProductSearchCondition cond(String name, Long minPrice, Long maxPrice, ProductStatus status, UUID categoryId) {
        return new ProductSearchCondition(name, minPrice, maxPrice, status, categoryId);
    }
    private static PageRequest firstPage() {
        return PageRequest.of(0, 20, Sort.unsorted());
    }

    @Test
    @DisplayName("이름 CONTAINS — 표식 3건")
    void byName() {
        var r = productRepository.search(cond(MARK, null, null, null, null), firstPage());
        assertThat(r.getContent()).hasSize(3);
    }

    @Test
    @DisplayName("가격 범위(>=20000) — 키보드·티셔츠 2건")
    void byPrice() {
        var r = productRepository.search(cond(MARK, 20_000L, null, null, null), firstPage());
        assertThat(r.getContent()).hasSize(2)
                .allSatisfy(p -> assertThat(p.getPrice()).isGreaterThanOrEqualTo(20_000));
    }

    @Test
    @DisplayName("상태 EQ(SOLD_OUT) — 티셔츠 1건")
    void byStatus() {
        var r = productRepository.search(cond(MARK, null, null, ProductStatus.SOLD_OUT, null), firstPage());
        assertThat(r.getContent()).hasSize(1);
        assertThat(r.getContent().get(0).getName()).contains("티셔츠");
    }

    @Test
    @DisplayName("카테고리 연관 필터(패션) — 티셔츠 1건")
    void byCategory() {
        var r = productRepository.search(cond(MARK, null, null, null, catFashionId), firstPage());
        assertThat(r.getContent()).hasSize(1);
        assertThat(r.getContent().get(0).getCategory().getId()).isEqualTo(catFashionId);
    }

    @Test
    @DisplayName("복합: 전자 + 가격<=20000 — 마우스 1건")
    void combined() {
        var r = productRepository.search(cond(MARK, null, 20_000L, null, catElecId), firstPage());
        assertThat(r.getContent()).hasSize(1);
        assertThat(r.getContent().get(0).getName()).contains("마우스");
    }
}
