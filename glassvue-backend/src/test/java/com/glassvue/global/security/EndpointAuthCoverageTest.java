package com.glassvue.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * <b>엔드포인트가 전부 «의도적으로» 분류돼 있는지</b>를 센다 — 경로 하나하나가 아니라 <b>덮개</b>를 지킨다.
 *
 * <p>🔴 <b>{@code SecurityConfig} 의 마지막 줄이 {@code .anyRequest().permitAll()} 이다.</b>
 * 즉 매처에 안 걸린 엔드포인트는 <b>열린다.</b> 빠뜨렸을 때 «막힌다» 가 아니라 «열린다» 쪽으로 넘어지므로,
 * 새 엔드포인트를 추가하면서 매처를 안 쓰면 <b>아무도 안 묻고 공개된다</b> — 컴파일도 테스트도 조용하다.
 *
 * <p>⚠ <b>권한 테스트가 없어서 만든 게 아니다.</b> 2026-09-07 실측으로 이미 401 단언 59건 · 403 단언 43건이
 * 24개 파일에 있다. 🔴 <b>다만 전부 «경로를 손으로 적은» 것이라 «빠진 경로»는 아무도 못 센다</b> —
 * 이 테스트가 세는 것이 그 «빠진 것» 하나다.
 *
 * <p>처방은 새로 지은 게 아니다. {@code OrderStatus.purchaseProven()} 이 2026-08-11 에 받은 것과 같다:
 * 열거를 손으로 늘리는 모양을 두면 <b>늘릴 계기를 아무도 못 받으므로</b>, 새 값이 <b>반드시 결정을 받게</b> 만든다.
 * 거기선 enum 옆의 플래그였고, 여기서는 아래 {@code PUBLIC} 목록이다.
 *
 * <p><b>고치는 법</b>: 이 테스트가 빨개지면 둘 중 하나다 —
 * ①정말 공개할 엔드포인트다 → {@code PUBLIC} 에 <b>이유와 함께</b> 추가한다.
 * ②아니다 → {@code SecurityConfig} 에 매처를 넣는다. <b>어느 쪽이든 «결정» 이 남는다</b> — 그게 목적이다.
 *
 * <p>⚠ 판정은 <b>비로그인 요청의 실제 응답</b>으로 한다(매처 규칙을 여기 베끼지 않는다). 베끼면
 * {@code SecurityConfig} 와 이 파일이 갈라지고, 갈라진 순간 <b>둘 다 초록인데 운영은 열린다.</b>
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
class EndpointAuthCoverageTest {

    @Autowired MockMvc mockMvc;
    // ⚠ 이 타입의 빈이 하나가 아니다 — actuator 가 자기 매핑을 따로 등록한다.
    // 이름으로 한정하지 않으면 NoUniqueBeanDefinitionException 으로 컨텍스트가 안 뜬다.
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping;

    /**
     * <b>비로그인에게 열려 있어도 되는 엔드포인트</b> — 2026-09-07 에 하나씩 판단했다.
     *
     * <p>⚠ <b>«지금 열려 있다» 가 아니라 «열려 있어야 한다» 는 목록이다.</b> 실측으로 채우면
     * 이 테스트는 현재 상태를 그대로 베껴 아무것도 안 지킨다.
     */
    private static final Set<String> PUBLIC = Set.of(
            // 로그인 전에 부를 수밖에 없는 것들 — 인증을 요구하면 로그인 자체가 불가능하다.
            "POST /api/auth/login",
            "POST /api/auth/signup",
            "POST /api/auth/refresh",
            "POST /api/auth/find-id",
            "POST /api/auth/password-reset/request",
            "POST /api/auth/password-reset/confirm",
            // 카탈로그 — 비회원이 둘러보는 화면이다(J 축이 그 동선을 다뤘다).
            "GET /api/products",
            "GET /api/products/{id}",
            "GET /api/categories",
            // ⚠ 리뷰·문의는 «목록은 공개, 내용은 마스킹» 이다 — 비밀글 본문은
            //    InquiryResponse.from 이 viewer 로 가린다(경로가 아니라 응답에서 갈린다).
            "GET /api/products/{productId}/reviews",
            "GET /api/products/{productId}/inquiries",
            // 공지 — 비회원도 읽는다. 조회수도 비회원 열람을 세야 맞는다.
            "GET /api/notices",
            "GET /api/notices/{id}",
            "POST /api/notices/{id}/views",
            // 정책 — 배송비·등급 기준은 장바구니 전에 보여야 한다.
            "GET /api/policy/shipping",
            "GET /api/policy/grades",
            // 쿠폰 «안내» 둘. ⚠ 받기(claim)는 공개가 아니다 — SecurityConfig 가 매처로 가른다.
            "GET /api/coupons/welcome",
            "GET /api/coupons/event");

    @Test
    @DisplayName("모든 API 엔드포인트는 «비로그인 401» 이거나 «의도적 공개» 둘 중 하나다")
    void everyEndpointIsDeliberatelyClassified() throws Exception {
        List<String> leaked = new ArrayList<>();   // 공개 목록에 없는데 비로그인에 열렸다
        List<String> stale = new ArrayList<>();    // 공개 목록에 있는데 막혔다(목록이 낡았다)
        Set<String> seen = new TreeSet<>();

        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            Set<String> patterns = info.getPatternValues();
            for (String pattern : patterns) {
                if (!pattern.startsWith("/api/")) {
                    continue;   // actuator·에러 경로는 이 테스트의 대상이 아니다
                }
                for (var method : info.getMethodsCondition().getMethods()) {
                    String key = method.name() + " " + pattern;
                    if (!seen.add(key)) {
                        continue;
                    }
                    String url = pattern.replaceAll("\\{[^}]*}", "00000000-0000-7000-8000-000000000000");
                    int status = mockMvc.perform(
                                    MockMvcRequestBuilders.request(HttpMethod.valueOf(method.name()), url)
                                            .contentType("application/json")
                                            .content("{}"))
                            .andReturn().getResponse().getStatus();
                    boolean declaredPublic = PUBLIC.contains(key);
                    if (status == 401 && declaredPublic) {
                        stale.add(key + " → 401 (공개라고 적어 뒀는데 막힌다)");
                    } else if (status != 401 && !declaredPublic) {
                        leaked.add(key + " → " + status + " (매처가 없어 비로그인에 열렸다)");
                    }
                }
            }
        }

        // 🔴 셈이 0 이면 «성립» 이 아니라 «안 돌았음» 일 수 있다(2026-09-02 가 그것에 물렸다).
        assertThat(seen).as("검사한 엔드포인트가 하나도 없다 — 핸들러 매핑을 못 읽은 것이다").isNotEmpty();

        assertThat(leaked)
                .as("""
                        비로그인에 열려 있는데 «공개» 라고 선언되지 않은 엔드포인트다.
                        SecurityConfig 의 기본이 permitAll 이라 매처를 빠뜨리면 이렇게 조용히 열린다.
                        → 공개가 맞으면 PUBLIC 에 이유와 함께 넣고, 아니면 SecurityConfig 에 매처를 넣을 것.""")
                .isEmpty();

        assertThat(stale)
                .as("PUBLIC 목록이 낡았다 — 막히도록 바뀐 엔드포인트가 아직 공개로 적혀 있다")
                .isEmpty();
    }
}
