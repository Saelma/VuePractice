package com.glassvue.domain.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.catalog.entity.Category;
import com.glassvue.domain.catalog.entity.Product;
import com.glassvue.domain.catalog.entity.ProductStatus;
import com.glassvue.domain.catalog.repository.CategoryRepository;
import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.domain.review.entity.Review;
import com.glassvue.domain.review.repository.ReviewRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리뷰 정렬·사진만 보기 (2026-08-03, 백로그 B-22).
 *
 * <p>⚠ <b>백엔드 정렬은 B-22 이전부터 있었다</b>(실측: `SORTABLE = createdAt·updatedAt·rating`).
 * 없던 것은 ①<b>화면이 `?sort` 를 안 보냈다</b>는 것과 ②<b>사진만 보기 필터</b>다.
 * 그래서 이 테스트는 <b>정렬이 실제로 동작하는지를 계약으로 고정</b>하고(화면이 이제 쓰기 시작하므로),
 * 필터는 새로 검증한다.
 *
 * <p>여기서만 드러나는 것 셋:
 * <ol>
 *   <li><b>화이트리스트가 진짜 막는가</b> — 허용 밖 필드는 500 이 아니라 <b>400</b>이어야 한다.</li>
 *   <li><b>필터가 목록과 카운트에 <i>함께</i> 걸리는가</b> — 한쪽만 걸리면 "N건" 과 줄 수가 어긋난다
 *       (B-16 에서 실제로 잡았던 자리).</li>
 *   <li>⚠ <b>요약 통계는 필터의 영향을 받지 않는가</b> — 사진 필터를 걸었다고 평균 별점이 달라지면
 *       상품 카드의 별점과 어긋나 <b>같은 상품인데 화면마다 다른 평점</b>이 뜬다.</li>
 * </ol>
 *
 * <p>리뷰는 <b>리포지토리로 직접</b> 만든다 — API 로 만들려면 "구매한 사람만" 규칙 때문에
 * 주문·결제를 통째로 태워야 하는데, 여기서 검증할 것은 <b>조회 쪽</b>이라 그 비용을 질 이유가 없다.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReviewSortFilterIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ReviewRepository reviewRepository;

    private UUID productId;
    private String url;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Category cat = categoryRepository.save(Category.builder().name("ZZC-리뷰" + suffix).build());
        productId = productRepository.save(Product.builder()
                .name("ZZP-리뷰상품" + suffix).description("d").price(10_000)
                .status(ProductStatus.SELLING).category(cat).build()).getId();
        url = "/api/products/" + productId + "/reviews";

        // 별점 3종 · 그중 하나만 사진 있음. 새 상품이라 이 리뷰들이 전부다(증분 걱정 없음).
        review(5, UUID.randomUUID());  // 사진 O
        review(1, null);               // 사진 X
        review(3, null);               // 사진 X
    }

    private void review(int rating, UUID imageGroupId) {
        reviewRepository.save(Review.builder()
                .productId(productId).authorId(UUID.randomUUID()).author("ZZ리뷰어")
                .rating(rating).content("내용").imageGroupId(imageGroupId).build());
    }

    private List<Integer> ratings(String query) throws Exception {
        String body = mockMvc.perform(get(url + query))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.page.content[*].rating");
    }

    @Test
    @DisplayName("기본은 최신순 — 정렬을 안 보내면 예전 동작 그대로다")
    void defaultsToLatest() throws Exception {
        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.content.length()").value(3));
    }

    @Test
    @DisplayName("별점 높은순 정렬 (`?sort=rating,desc`)")
    void sortByRatingDesc() throws Exception {
        assertThat(ratings("?sort=rating,desc")).containsExactly(5, 3, 1);
    }

    @Test
    @DisplayName("별점 낮은순 정렬 (`?sort=rating,asc`) — 낮은 별점부터 보고 싶을 때")
    void sortByRatingAsc() throws Exception {
        assertThat(ratings("?sort=rating,asc")).containsExactly(1, 3, 5);
    }

    @Test
    @DisplayName("⚠ 허용 밖 필드는 **400** — 화이트리스트가 없으면 500이 난다")
    void rejectsUnknownSortField() throws Exception {
        mockMvc.perform(get(url + "?sort=content,desc"))
                .andExpect(status().isBadRequest());
        // 존재하지 않는 컬럼도 마찬가지 — 임의 문자열이 SQL 로 새어 나가지 않는다
        mockMvc.perform(get(url + "?sort=nonexistent,desc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("사진 있는 리뷰만 (`?photoOnly=true`)")
    void photoOnlyFilter() throws Exception {
        assertThat(ratings("?photoOnly=true"))
                .as("사진이 있는 건 별점 5짜리 하나뿐이다").containsExactly(5);
    }

    @Test
    @DisplayName("⚠ 필터가 **목록과 총 건수에 함께** 걸린다 — 한쪽만 걸리면 숫자와 줄이 어긋난다")
    void filterAppliesToCountToo() throws Exception {
        // ⚠ **count 쿼리가 실제로 돌게 만들어야 한다.** PageableExecutionUtils 는 첫 페이지에서
        //    결과가 페이지 크기보다 작으면 **count 를 아예 실행하지 않고** content.size() 를 쓴다.
        //    처음엔 사진 리뷰 1건 · size 기본값으로 검증했는데, 그래서 **count 를 망가뜨린 변형이
        //    안 잡혔다**(2026-08-03 변형 주입에서 드러남). 사진 리뷰를 page size 보다 많이 만들어
        //    count 경로를 강제로 태운다.
        review(5, UUID.randomUUID());
        review(4, UUID.randomUUID());
        review(2, UUID.randomUUID());   // 사진 리뷰 총 4건(setUp 의 1 + 여기 3)

        mockMvc.perform(get(url + "?photoOnly=true&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.content.length()").value(2))   // 한 페이지엔 2건
                .andExpect(jsonPath("$.data.page.totalElements").value(4))     // ← count 가 여기서 돈다
                .andExpect(jsonPath("$.data.page.totalPages").value(2));
    }

    @Test
    @DisplayName("photoOnly=false 는 전부 준다 (기본값과 같다)")
    void photoOnlyFalseReturnsAll() throws Exception {
        assertThat(ratings("?photoOnly=false")).hasSize(3);
    }

    @Test
    @DisplayName("⚠ **요약 통계는 필터의 영향을 받지 않는다** — 그 상품 전체의 평점이라야 카드와 안 어긋난다")
    void statsIgnoreFilter() throws Exception {
        String all = mockMvc.perform(get(url))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String photo = mockMvc.perform(get(url + "?photoOnly=true"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        Number avgAll = JsonPath.read(all, "$.data.averageRating");
        Number avgPhoto = JsonPath.read(photo, "$.data.averageRating");
        Number cntAll = JsonPath.read(all, "$.data.reviewCount");
        Number cntPhoto = JsonPath.read(photo, "$.data.reviewCount");

        assertThat(avgPhoto).as("평균 별점은 필터와 무관해야 한다").isEqualTo(avgAll);
        assertThat(cntPhoto).as("리뷰 개수도 마찬가지 — 전체 기준이다").isEqualTo(cntAll);
        assertThat(cntAll.intValue()).as("전제: 이 상품 리뷰는 3건이다").isEqualTo(3);
    }

    @Test
    @DisplayName("정렬과 필터를 함께 걸 수 있다")
    void sortAndFilterTogether() throws Exception {
        review(4, UUID.randomUUID());  // 사진 있는 리뷰 하나 더

        assertThat(ratings("?photoOnly=true&sort=rating,asc"))
                .as("사진 있는 것만(5·4) 낮은 별점부터").containsExactly(4, 5);
    }
}
