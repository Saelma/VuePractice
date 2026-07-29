package com.glassvue.domain.point.dto;

import com.glassvue.domain.point.entity.MemberGrade;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;
import java.util.List;

/**
 * 등급 정책 한 줄(2026-07-29). <b>로그인 전에도</b> 등급·적립률을 보여주기 위한 공개 정보다.
 *
 * <p>{@code /api/points/me} 는 <b>내 등급</b>을 주지만 비로그인에는 쓸 수 없다. 홈의 혜택 안내가
 * "최대 5% 적립"을 말하려면 <b>정책 표 자체</b>가 필요해서 이걸 연다.
 *
 * <p>⚠ 화면이 "1~5%" 를 직접 적지 않게 하려는 것이 목적이다 — {@link MemberGrade} 를 고쳤는데
 * 홈 문구가 그대로면 <b>안내만 거짓말</b>이 된다(PointPanel 이 임계값을 서버에서 받는 것과 같은 판단).
 */
@Schema(description = "회원 등급 정책")
public record GradePolicyResponse(

        @Schema(description = "등급", example = "BRONZE")
        MemberGrade grade,

        @Schema(description = "이 등급이 되는 누적 구매확정액(원)", example = "0")
        long minPurchase,

        @Schema(description = "적립률(%)", example = "1")
        int earnPercent
) {
    /** enum 선언 순서 = 등급 오름차순이라 그대로 내보낸다(정렬 규칙을 따로 두면 어긋난다). */
    public static List<GradePolicyResponse> all() {
        return Arrays.stream(MemberGrade.values())
                .map(g -> new GradePolicyResponse(g, g.minPurchase(), g.earnPercent()))
                .toList();
    }
}
