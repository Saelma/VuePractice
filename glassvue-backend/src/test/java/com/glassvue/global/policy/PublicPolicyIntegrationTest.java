package com.glassvue.global.policy;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.point.entity.MemberGrade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 정책 API (2026-07-29) — 비로그인 홈의 혜택 안내가 읽는 값들.
 *
 * <p><b>이 테스트가 지키는 것은 "화면이 정책 숫자를 갖지 않는다"</b>는 규칙이다. 두 엔드포인트가
 * 조용히 401 이 되거나(보호 규칙이 넓어지면) 응답 필드 이름이 바뀌면, 홈은 안내를 **말없이 감추고**
 * 아무도 모른 채 배포된다(값이 null 이면 v-if 로 사라지는 설계라 에러도 안 난다). 그래서 계약을 못박는다.
 *
 * <p>⚠ 값 자체(3000·30000·1~5%)는 단언하지 않는다 — 설정·정책이 바뀌면 그건 <b>정상적인 변경</b>이고,
 * 테스트가 정책 숫자를 복사해 두면 "화면에 하드코딩하지 말자"는 이 작업의 취지를 테스트가 어기는 꼴이 된다.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PublicPolicyIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("배송비 정책: 비로그인 200 + fee·freeThreshold 를 준다")
    void shippingPolicy_public() throws Exception {
        mockMvc.perform(get("/api/policy/shipping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fee").isNumber())
                .andExpect(jsonPath("$.data.freeThreshold").isNumber());
    }

    @Test
    @DisplayName("등급 정책표: 비로그인 200 + 등급 수만큼, 각 행에 earnPercent 가 있다")
    void grades_public() throws Exception {
        mockMvc.perform(get("/api/policy/grades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(MemberGrade.values().length))
                .andExpect(jsonPath("$.data[0].grade").value(MemberGrade.values()[0].name()))
                .andExpect(jsonPath("$.data[0].earnPercent").isNumber())
                .andExpect(jsonPath("$.data[0].minPurchase").isNumber());
    }

    @Test
    @DisplayName("등급 정책표는 enum 선언 순서(등급 오름차순)를 그대로 따른다")
    void grades_orderFollowsEnum() throws Exception {
        var result = mockMvc.perform(get("/api/policy/grades")).andExpect(status().isOk());
        MemberGrade[] all = MemberGrade.values();
        for (int i = 0; i < all.length; i++) {
            result.andExpect(jsonPath("$.data[" + i + "].grade").value(all[i].name()));
        }
    }
}
