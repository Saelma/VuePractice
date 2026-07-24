package com.glassvue.domain.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.catalog.entity.Category;
import com.glassvue.domain.catalog.entity.Product;
import com.glassvue.domain.catalog.entity.ProductStatus;
import com.glassvue.domain.catalog.repository.CategoryRepository;
import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카테고리 삭제(`DELETE /api/categories/{id}`) — 권한(401/403/200) + 소속 상품 있을 때 409.
 *
 * <p><b>권한 테스트를 반드시 두는 이유</b>: 권한 규칙(hasRole/authenticated)은 서비스 단위 테스트로는
 * 절대 안 잡히고 실제 요청을 보내야만 드러난다(2026-07-20 이미지 업로드 ADMIN 사고와 같은 계열).
 * 새 매처를 SecurityConfig에 추가했으니 그 매처가 실제로 먹는지 여기서 고정한다.
 *
 * <p>DB_HOST 있을 때만 실행(= .env 소싱). @Transactional 롤백 → 공유 DB 무오염.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CategoryAdminIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String PW = "password123";
    private static final String MARK = "ZZCATADMIN";

    private String adminLoginId;
    private String userLoginId;
    private UUID categoryId;

    @BeforeEach
    void setUp() {
        adminLoginId = "zzadm_" + UUID.randomUUID().toString().substring(0, 8);
        userLoginId = "zzusr_" + UUID.randomUUID().toString().substring(0, 8);
        member(adminLoginId, MARK + "-관리자", Role.ADMIN);
        member(userLoginId, MARK + "-사용자", Role.USER);
        categoryId = categoryRepository.save(
                Category.builder().name(MARK + "-" + UUID.randomUUID().toString().substring(0, 8)).build()).getId();
    }

    private void member(String loginId, String nickname, Role role) {
        memberRepository.save(Member.builder()
                .loginId(loginId).password(passwordEncoder.encode(PW))
                .nickname(nickname).role(role).build());
    }

    private String login(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    @Test
    @DisplayName("비로그인 삭제 → 401")
    void anonymous_unauthorized() throws Exception {
        mockMvc.perform(delete("/api/categories/" + categoryId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("일반 사용자(USER) 삭제 → 403")
    void user_forbidden() throws Exception {
        mockMvc.perform(delete("/api/categories/" + categoryId).header("Authorization", login(userLoginId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자: 빈 카테고리 삭제 → 200, 실제로 사라진다")
    void admin_deleteEmpty_ok() throws Exception {
        mockMvc.perform(delete("/api/categories/" + categoryId).header("Authorization", login(adminLoginId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        org.assertj.core.api.Assertions.assertThat(categoryRepository.existsById(categoryId)).isFalse();
    }

    @Test
    @DisplayName("관리자: 소속 상품 있는 카테고리 삭제 → 409 CATEGORY_IN_USE")
    void admin_deleteInUse_conflict() throws Exception {
        Category category = categoryRepository.findById(categoryId).orElseThrow();
        productRepository.save(Product.builder()
                .name(MARK + "-상품").description("d").price(1000)
                .status(ProductStatus.SELLING).category(category).build());

        mockMvc.perform(delete("/api/categories/" + categoryId).header("Authorization", login(adminLoginId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CATEGORY-409U"));
    }
}
