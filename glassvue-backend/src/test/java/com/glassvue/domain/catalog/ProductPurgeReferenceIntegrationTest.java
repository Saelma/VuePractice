package com.glassvue.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.glassvue.domain.catalog.entity.Category;
import com.glassvue.domain.catalog.entity.Product;
import com.glassvue.domain.catalog.entity.ProductStatus;
import com.glassvue.domain.catalog.repository.CategoryRepository;
import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.domain.catalog.service.command.ProductCommandService;
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.glassvue.domain.restock.entity.RestockSubscription;
import com.glassvue.domain.restock.repository.RestockSubscriptionRepository;
import com.glassvue.domain.wishlist.entity.Wishlist;
import com.glassvue.domain.wishlist.repository.WishlistRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 영구 삭제가 <b>상품을 가리키던 줄</b>을 어떻게 처리하나 (2026-09-11, BACKLOG O-7 · WA §2-12).
 *
 * <p>🔴 <b>찜·재입고 구독이 영원히 남고 있었다</b> — FK 도, 영구 삭제 이벤트를 받는 곳도 없었다(받는 곳은 알림 하나).
 * 지금 고아는 0건이라 «청소» 가 아니라 <b>재발 방지</b>다 — M-4 는 알림 링크 55건이 쌓인 뒤에야 찾았다.
 *
 * <p>⚠ 덮개는 <b>DB 에서</b> 목록을 꺼낸다({@code user_tab_columns}) — 엔티티를 손으로 적으면 새 테이블이 빠진다(§3-6).
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@Transactional
class ProductPurgeReferenceIntegrationTest {

    @Autowired ProductCommandService productCommandService;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired WishlistRepository wishlistRepository;
    @Autowired RestockSubscriptionRepository restockRepository;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbcTemplate;

    /** FK ON DELETE CASCADE 로 상품과 함께 지워진다 — 아래 덮개가 DB 의 삭제 규칙을 <b>직접 읽어</b> 확인한다. */
    private static final Set<String> CASCADE = Set.of("PRODUCT_VARIANT", "STOCK_HISTORY", "PRODUCT_DISCOUNT");

    /** 영구 삭제 이벤트({@code ProductPurgedEvent})를 받아 각 도메인이 치운다 — 아래 동작 시험이 확인한다. */
    private static final Set<String> PURGED_BY_EVENT = Set.of("WISHLIST", "RESTOCK_SUBSCRIPTION");

    /** 일부러 남긴다 — 이유와 함께. */
    private static final Map<String, String> KEPT = Map.of(
            "REVIEW", "사람이 쓴 글 — 상품이 없어도 읽을 값이 있고, 화면이 상품 이름 없음을 견딘다(ProductCommandService.purge 주석)",
            "INQUIRY", "사람이 쓴 글 — 리뷰와 같다",
            "ORDER_ITEM", "주문 스냅샷 — 매출·환불·반품의 원장이다. 상품명·단가를 스스로 들고 있다");

    private UUID member() {
        String s = UUID.randomUUID().toString().substring(0, 8);
        return memberRepository.save(Member.builder().loginId("zzpurge_" + s).password("x")
                .nickname("ZZ정리" + s).role(Role.USER).build()).getId();
    }

    private UUID product(Category cat, String name) {
        return productRepository.save(Product.builder().name(name).description("d").price(1_000)
                .status(ProductStatus.SELLING).category(cat).build()).getId();
    }

    @Test
    @DisplayName("🔴 영구 삭제하면 그 상품의 찜·재입고 구독이 사라진다 — 다른 상품 것은 남는다")
    void purgeCleansWishlistAndRestock() {
        Category cat = categoryRepository.save(Category.builder().name("ZZC-정리" + UUID.randomUUID()).build());
        UUID gone = product(cat, "ZZP-지울상품");
        UUID stays = product(cat, "ZZP-남을상품");   // 대조군 — «전부 지운다» 와 구별된다
        UUID m = member();
        wishlistRepository.save(Wishlist.of(m, gone));
        wishlistRepository.save(Wishlist.of(m, stays));
        restockRepository.save(RestockSubscription.of(m, gone));
        restockRepository.save(RestockSubscription.of(m, stays));
        entityManager.flush();

        productCommandService.purge(gone);
        entityManager.flush();
        entityManager.clear();

        assertThat(count("WISHLIST", gone)).isZero();
        assertThat(count("RESTOCK_SUBSCRIPTION", gone)).isZero();
        assertThat(count("WISHLIST", stays)).isEqualTo(1);
        assertThat(count("RESTOCK_SUBSCRIPTION", stays)).isEqualTo(1);
    }

    private int count(String table, UUID productId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where product_id = hextoraw(?)", Integer.class,
                productId.toString().replace("-", ""));
    }

    @Test
    @DisplayName("🔴 product_id 칸을 가진 테이블은 전부 «CASCADE / 이벤트로 치움 / 이유 있게 남김» 중 하나로 정해져 있다")
    void everyProductReferenceIsDecided() {
        Set<String> tables = new TreeSet<>(jdbcTemplate.queryForList(
                "select table_name from user_tab_columns where column_name = 'PRODUCT_ID'", String.class));
        assertThat(tables).as("PRODUCT_ID 칸을 하나도 못 찾았다 — 조회가 헛돈다(WA §3-6)").isNotEmpty();

        List<String> undecided = tables.stream()
                .filter(t -> !CASCADE.contains(t) && !PURGED_BY_EVENT.contains(t) && !KEPT.containsKey(t)).toList();
        assertThat(undecided)
                .as("""
                        상품을 가리키는데 영구 삭제 때 어떻게 할지 정해지지 않은 테이블이다.
                        빠뜨리면 없는 상품을 가리키는 줄이 조용히 쌓인다(M-4 알림 55건).
                        → FK CASCADE · ProductPurgedEvent 리스너 · KEPT(이유) 중 하나로.""")
                .isEmpty();

        Set<String> listed = new TreeSet<>(CASCADE);
        listed.addAll(PURGED_BY_EVENT);
        listed.addAll(KEPT.keySet());
        listed.removeAll(tables);
        assertThat(listed).as("목록에 있는데 DB 에 그 칸이 없다 — 목록이 낡았다").isEmpty();
    }

    @Test
    @DisplayName("CASCADE 라고 적은 테이블은 DB 의 FK 삭제 규칙이 실제로 CASCADE 다 — 손으로 적은 말을 믿지 않는다")
    void cascadeIsRealInDatabase() {
        Set<String> cascaded = new TreeSet<>(jdbcTemplate.queryForList("""
                select cc.table_name from user_cons_columns cc
                  join user_constraints uc on uc.constraint_name = cc.constraint_name
                 where cc.column_name = 'PRODUCT_ID' and uc.constraint_type = 'R' and uc.delete_rule = 'CASCADE'
                """, String.class));
        assertThat(cascaded).isEqualTo(new TreeSet<>(CASCADE));
    }
}
