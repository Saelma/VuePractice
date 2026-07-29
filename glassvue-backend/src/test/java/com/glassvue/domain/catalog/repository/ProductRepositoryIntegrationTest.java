package com.glassvue.domain.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassvue.domain.catalog.dto.ProductSearchCondition;
import com.glassvue.domain.catalog.entity.Category;
import com.glassvue.domain.catalog.entity.Product;
import com.glassvue.domain.catalog.entity.ProductStatus;
import com.glassvue.global.config.JpaAuditingConfig;
import com.glassvue.global.exception.BusinessException;
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
    @Autowired jakarta.persistence.EntityManager entityManager;

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
                .name(name).description("d").price(price).status(status).category(category).build());
    }

    private ProductSearchCondition cond(String name, Long minPrice, Long maxPrice, ProductStatus status, UUID categoryId) {
        return new ProductSearchCondition(name, minPrice, maxPrice, status, categoryId);
    }
    private static PageRequest firstPage() {
        return PageRequest.of(0, 20, Sort.unsorted());
    }

    @Test
    @DisplayName("정렬: 가격 오름차순 — 화이트리스트 필드는 실제로 정렬된다")
    void sortByPriceAsc() {
        var r = productRepository.search(cond(MARK, null, null, null, null),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "price")));
        assertThat(r.getContent()).extracting(Product::getPrice).containsExactly(15_000L, 20_000L, 30_000L);
    }

    @Test
    @DisplayName("정렬: avgRating — 커머스 기본인 평점순을 허용한다(V4 비정규화 컬럼이라 조인 불필요)")
    void sortByAvgRating() {
        var r = productRepository.search(cond(MARK, null, null, null, null),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "avgRating")));
        assertThat(r.getContent()).hasSize(3); // 값이 모두 0이라 순서가 아니라 "거부되지 않음"을 본다
    }

    @Test
    @DisplayName("정렬: reviewCount 내림차순 — 리뷰 많은순이 실제로 정렬된다 (2026-07-29)")
    void sortByReviewCount() {
        // avgRating 테스트가 "거부되지 않음"만 보는 것과 달리, 여기선 값을 넣어 **순서**까지 본다.
        // ⚠ 벌크 UPDATE 직후의 값은 엔티티로 읽지 않는다(ARCHITECTURE §3) — clear 로 1차 캐시를 비운다.
        var all = productRepository.search(cond(MARK, null, null, null, null), firstPage()).getContent();
        productRepository.updateRating(all.get(0).getId(), 4.0, 7);
        productRepository.updateRating(all.get(1).getId(), 5.0, 1);
        productRepository.updateRating(all.get(2).getId(), 3.0, 3);
        entityManager.flush();
        entityManager.clear();

        var r = productRepository.search(cond(MARK, null, null, null, null),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "reviewCount")));
        assertThat(r.getContent()).extracting(Product::getReviewCount).containsExactly(7L, 3L, 1L);

        // 평점순과 순서가 다르다는 것이 이 정렬을 연 이유다 — 별 5개 리뷰 1건이 위로 오는 왜곡을 보완한다.
        var byRating = productRepository.search(cond(MARK, null, null, null, null),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "avgRating")));
        assertThat(byRating.getContent().get(0).getReviewCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("정렬: 화이트리스트 밖 필드는 거부한다(임의 컬럼 정렬 차단)")
    void sortByNotAllowedField() {
        assertThatThrownBy(() -> productRepository.search(cond(MARK, null, null, null, null),
                PageRequest.of(0, 20, Sort.by("description"))))
                .isInstanceOf(BusinessException.class);
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

    @Test
    @DisplayName("정렬: soldCount — 홈 인기순을 허용한다(V25 비정규화 컬럼이라 조인 불필요)")
    void sortBySoldCount() {
        var r = productRepository.search(cond(MARK, null, null, null, null),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "soldCount")));
        assertThat(r.getContent()).hasSize(3); // 거부되지 않고 정렬됨(값은 모두 0)
    }

    @Test
    @DisplayName("addSoldCount: 증감이 반영되고, 음수로는 내려가지 않는다(0에서 막힘)")
    void addSoldCountClampsAtZero() {
        Category c = categoryRepository.save(Category.builder().name("ZZC-집계").build());
        Product p = productRepository.save(Product.builder()
                .name(MARK + "-집계상품").description("d").price(1_000).status(ProductStatus.SELLING).category(c).build());
        UUID id = p.getId();

        assertThat(productRepository.addSoldCount(id, 5)).isEqualTo(1);
        assertThat(productRepository.addSoldCount(id, -2)).isEqualTo(1); // 3
        // 남은 3에서 10을 빼도 음수(-7)가 아니라 0으로 막힌다(잔액 CHECK 와 같은 방어선).
        assertThat(productRepository.addSoldCount(id, -10)).isEqualTo(1);

        // 벌크 UPDATE라 1차 캐시가 낡았다 — 지우고 DB에서 다시 읽어야 실제 값이 보인다.
        entityManager.flush();
        entityManager.clear();
        assertThat(productRepository.findById(id).orElseThrow().getSoldCount()).isZero();
    }

    @Test
    @DisplayName("addSoldCount: 없는 상품이면 0행(이미 삭제됨) — 예외 아님")
    void addSoldCountMissingProduct() {
        assertThat(productRepository.addSoldCount(UUID.randomUUID(), 3)).isZero();
    }
}
