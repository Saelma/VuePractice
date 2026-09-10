package com.glassvue.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * <b>공개 응답에 «누구의 id» 가 실려 나가는지</b>를 센다 — O 축의 짝이다.
 *
 * <p>🔴 O 축(2026-09-07)은 «<b>누가</b> 부를 수 있나» 를 셌다. 그런데 «부를 수 있는 사람에게
 * <b>무엇까지</b> 주나» 는 아무도 안 셌다 — 모양은 O 와 같고 대상이 «경로» 가 아니라 «내용» 이다.
 *
 * <p>⚠ <b>왜 UUID 칸이 문제인가.</b> 이 프로젝트의 PK 는 <b>UUIDv7</b> 이라 앞 48비트가
 * <b>생성 시각(ms)</b> 이다. 회원 id 를 공개 응답에 실으면 <b>가입 시각이 토큰 없이 새어 나간다</b> —
 * 2026-09-10 에 상품 문의·리뷰 목록을 비로그인으로 불러 <b>실제 회원 세 명의 가입 시각을 초 단위로
 * 복원</b>했다. 「id 는 그냥 불투명한 값」이라는 통념이 이 PK 선택에서는 <b>틀리다</b>.
 *
 * <p>⚠ <b>«공개인가» 를 SecurityConfig 에서 베끼지 않는다</b> — 비로그인으로 <b>실제로 불러</b>
 * 401/403 이 아니면 공개로 친다. 베끼면 설정과 이 파일이 갈라지고, 갈라진 순간 둘 다 초록인데
 * 운영은 샌다({@link EndpointAuthCoverageTest} 와 같은 이유다).
 *
 * <p><b>고치는 법</b>: 빨개지면 둘 중 하나다 — ①그 칸은 나가도 된다(리소스 자신의 id 처럼)
 * → {@code ALLOWED} 에 <b>이유와 함께</b> 넣는다. ②아니다 → 응답에서 뺀다. 어느 쪽이든 결정이 남는다.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
class PublicResponseExposureTest {

    @Autowired MockMvc mockMvc;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping;

    /**
     * <b>비로그인 응답에 실려도 되는 UUID 칸</b> — {@code 레코드.칸} 으로 적는다.
     *
     * <p>⚠ 기준은 <b>«그 값이 그 리소스 자신을 가리키는가»</b> 다. 자기 id 와 «어느 상품/분류에
     * 속하나» 는 화면이 링크를 걸려면 있어야 한다. <b>«누가 썼나»(회원 id)는 여기 못 들어온다</b> —
     * 그건 리소스의 신원이 아니라 <b>사람</b>의 신원이다.
     */
    private static final Set<String> ALLOWED = Set.of(
            // 리소스 자신의 id — 상세 이동·수정·삭제가 전부 이 값으로 간다.
            "ProductResponse.id",
            "CategoryResponse.id",
            "NoticeResponse.id",
            "InquiryResponse.id",
            "ReviewResponse.id",
            "ImageResponse.id",
            "VariantResponse.id",
            "ProductDiscountResponse.id",
            "MemberCouponResponse.id",
            "CouponResponse.id",
            // 소속 — 「이 문의가 어느 상품 것인가」. 상품 id 는 어차피 공개 카탈로그의 값이다.
            "InquiryResponse.productId",
            "ReviewResponse.productId",
            "ProductResponse.categoryId",
            "VariantResponse.productId",
            "ProductDiscountResponse.productId",
            "CategoryResponse.parentId",
            // 쿠폰 «안내» 가 가리키는 쿠폰 — 받기(claim)가 이 id 로 간다. 사람이 아니라 발행물의 id 다.
            "EventCouponResponse.couponId");

    @Test
    @DisplayName("비로그인이 받는 응답에는 «사람의 id» 가 실리지 않는다")
    void publicResponsesCarryNoMemberIds() throws Exception {
        Set<String> found = new TreeSet<>();
        Set<String> probed = new TreeSet<>();

        for (var entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod handler = entry.getValue();
            if (!info.getMethodsCondition().getMethods().contains(
                    org.springframework.web.bind.annotation.RequestMethod.GET)) {
                continue;   // 조회만 본다 — 「받아 가는 것」이 이 축의 대상이다
            }
            for (String pattern : info.getPatternValues()) {
                if (!pattern.startsWith("/api/")) {
                    continue;
                }
                String url = pattern.replaceAll("\\{[^}]*}", "00000000-0000-7000-8000-000000000000");
                int status = mockMvc.perform(MockMvcRequestBuilders.get(url))
                        .andReturn().getResponse().getStatus();
                if (status == 401 || status == 403) {
                    continue;   // 막혀 있다 — 이 축의 대상이 아니다
                }
                probed.add(pattern);
                collectUuidFields(handler.getReturnType().getGenericParameterType(),
                        new HashSet<>(), found);
            }
        }

        // 🔴 「하나도 못 훑었다」와 「훑었는데 깨끗하다」는 다르다 (WA §3-6).
        assertThat(probed)
                .as("공개 GET 을 하나도 못 찾았다 — 이 테스트는 아무것도 안 지키고 있다")
                .isNotEmpty();

        found.removeAll(ALLOWED);
        assertThat(found)
                .as("공개 응답에 판단받지 않은 UUID 칸이 있다. 리소스 자신의 id 면 ALLOWED 에 "
                        + "이유와 함께 넣고, 사람의 id 면 응답에서 뺀다. (훑은 공개 GET: %s)", probed)
                .isEmpty();
    }

    /** 응답 타입을 레코드 칸까지 재귀로 훑어 {@code UUID} 칸을 {@code 레코드.칸} 으로 모은다. */
    private void collectUuidFields(Type type, Set<Type> visited, Set<String> out) {
        if (type == null || !visited.add(type)) {
            return;
        }
        if (type instanceof ParameterizedType p) {
            collectUuidFields(p.getRawType(), visited, out);
            for (Type arg : p.getActualTypeArguments()) {
                collectUuidFields(arg, visited, out);
            }
            return;
        }
        if (!(type instanceof Class<?> c) || !c.isRecord()) {
            return;
        }
        for (RecordComponent rc : c.getRecordComponents()) {
            if (rc.getType() == UUID.class) {
                out.add(c.getSimpleName() + "." + rc.getName());
            } else {
                collectUuidFields(rc.getGenericType(), visited, out);
            }
        }
    }
}
