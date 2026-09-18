package com.glassvue.global.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.auth.service.AuthService;
import com.glassvue.domain.catalog.entity.Product;
import com.glassvue.domain.catalog.entity.ProductStatus;
import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.domain.catalog.repository.ProductVariantRepository;
import com.glassvue.domain.catalog.service.CategoryService;
import com.glassvue.domain.catalog.service.command.ProductCommandService;
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.glassvue.domain.point.repository.PointAccountRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시작 데이터 (2026-09-18, BACKLOG F-3).
 *
 * <p>⚠ <b>러너({@code seed} 프로파일)는 여기서 안 띄운다</b> — 공유 espdb 는 비어 있지 않아 러너가 거절하고
 * {@code System.exit} 로 테스트 JVM 을 내린다. 그래서 둘로 나눠 본다:
 * ① «비었나» 가 공유 DB 에서 <b>거짓</b>이다(= 러너는 운영에 손대지 않는다)
 * ② 채우는 부분을 <b>롤백 안에서</b>, 이름에 무작위 꼬리를 붙여 공유 DB 와 안 겹치게 돌린다.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SeedDataIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired MemberRepository memberRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired CategoryService categoryService;
    @Autowired ProductCommandService productCommandService;
    @Autowired PointAccountRepository pointAccountRepository;

    private SeedData seed(String suffix) {
        return new SeedData(authService, memberRepository, productRepository,
                categoryService, productCommandService, suffix);
    }

    @Test
    @DisplayName("🔴 운영 DB 는 «비어 있지 않다» — 러너는 여기서 거절한다(dev 가 운영과 같은 espdb 에 붙는다)")
    void sharedDbIsNotEmpty() {
        long members = memberRepository.count();
        long products = productRepository.count();

        assertThat(seed("").isEmpty()).isFalse();
        // 묻기만 하고 아무것도 안 바꾼다.
        assertThat(memberRepository.count()).isEqualTo(members);
        assertThat(productRepository.count()).isEqualTo(products);
    }

    @Test
    @DisplayName("🔴 관리자는 **최상위**로, 회원은 일반으로 — API 로는 최상위를 못 주니 여기가 유일한 길이다")
    void createsSuperAdminAndUser() {
        SeedData.Result r = seed(suffix()).populate();

        Member admin = memberRepository.findByLoginId(r.adminLoginId()).orElseThrow();
        Member user = memberRepository.findByLoginId(r.userLoginId()).orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.SUPER_ADMIN);
        assertThat(user.getRole()).isEqualTo(Role.USER);
        // 가입 서비스를 거쳤다는 증거 — SQL 로 넣었으면 적립금 계정이 없다.
        assertThat(pointAccountRepository.findByMemberId(admin.getId())).isPresent();
        assertThat(pointAccountRepository.findByMemberId(user.getId())).isPresent();
    }

    @Test
    @DisplayName("🔴 찍어 준 비밀번호로 **실제로 로그인된다** — BCrypt 로 저장됐고 정책도 통과했다")
    void printedPasswordsLogIn() throws Exception {
        SeedData.Result r = seed(suffix()).populate();

        for (String[] cred : new String[][]{{r.adminLoginId(), r.adminPassword()}, {r.userLoginId(), r.userPassword()}}) {
            mockMvc.perform(post("/api/auth/login").contentType("application/json")
                            .content("{\"loginId\":\"" + cred[0] + "\",\"password\":\"" + cred[1] + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
        }
        // ⚠ 두 비밀번호는 서로 다르다 — 같은 값이면 한쪽이 새면 둘 다 샌다.
        assertThat(r.adminPassword()).isNotEqualTo(r.userPassword()).hasSize(16);
    }

    @Test
    @DisplayName("카테고리 3 · 상품 8 · 옵션 11 — 화면에 한 번씩 보일 표본(품절 1 · 정가 취소선 1 · 옵션 가격차 1 · 재고 부족 1)")
    void createsCatalogSamples() {
        SeedData.Result r = seed(suffix()).populate();

        assertThat(r.categoryIds()).hasSize(3);
        assertThat(r.productIds()).hasSize(8);
        List<Product> products = productRepository.findAllById(r.productIds());
        assertThat(products).hasSize(8);
        assertThat(products).filteredOn(p -> p.getStatus() == ProductStatus.SOLD_OUT).hasSize(1);
        assertThat(products).filteredOn(p -> p.getListPrice() != null).hasSize(1);
        assertThat(r.productIds().stream().mapToLong(variantRepository::countByProductId).sum()).isEqualTo(11);
        assertThat(r.productIds().stream()
                .flatMap(id -> variantRepository.findByProductIdOrderBySortOrderAscCreatedAtAsc(id).stream())
                .filter(v -> v.getPriceDelta() > 0)).hasSize(1);
    }

    /** 공유 DB 의 아이디·카테고리 이름과 안 겹치게 — 롤백되지만 유니크 검사는 커밋 전에도 돈다. */
    private static String suffix() {
        return "_" + UUID.randomUUID().toString().substring(0, 6);
    }
}
