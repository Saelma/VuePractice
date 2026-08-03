package com.glassvue.domain.notification.controller;

import com.glassvue.domain.notification.dto.MarketingSendRequest;
import com.glassvue.domain.notification.dto.MarketingSendResponse;
import com.glassvue.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

/**
 * 관리자 마케팅 발송 API (2026-08-03, B-21 후속).
 *
 * <p>경로가 {@code /api/admin/**} 이라 SecurityConfig 한 줄이 이미 ADMIN 으로 막는다(WA §2-4).
 *
 * <p>⚠ 사용자용 알림 API({@code /api/notifications})는 <b>읽음 처리만</b> 한다 — 알림을 <b>만드는</b>
 * 경로는 지금까지 이벤트 핸들러뿐이었다. 여기가 <b>사람이 알림을 만드는 첫 자리</b>라 경로를 관리자
 * 쪽으로 완전히 갈라 둔다.
 */
@Tag(name = "AdminMarketing", description = "관리자 마케팅 알림 발송 API")
public interface AdminMarketingController {

    @Operation(summary = "마케팅 알림 발송 대상 수",
            description = """
                    **마케팅 수신에 동의한** 회원 수를 돌려준다(`member.marketing_agreed_at IS NOT NULL`).

                    ⚠ 이 수는 **동의자 수**이지 실제 발송될 수가 아니다 — 알림 설정에서 마케팅을 끈 회원은
                    발송에서 빠지므로 **실제 발송은 이보다 적을 수 있다.** 화면 문구가 이걸 단정하면 안 된다.
                    """)
    ResponseEntity<ApiResponse<Integer>> audience();

    @Operation(summary = "마케팅 알림 발송",
            description = """
                    **동의자 전원**에게 인앱 알림(`MARKETING`)을 만든다. 대상은 **서버가 정한다** —
                    요청으로 대상을 지정할 수 없다(동의하지 않은 회원에게 보내는 구멍을 만들지 않기 위해).

                    두 조건을 **모두** 만족해야 발송된다:
                    1. `member.marketing_agreed_at IS NOT NULL` — **동의했나**(근거)
                    2. 알림 설정에서 `MARKETING` 을 끄지 않았나 — **지금 받고 싶나**(선호)

                    응답은 `agreed`(동의자) · `sent`(실제 발송) · `optedOut`(수신 거부로 제외)로 **나눠서** 준다.
                    합쳐서 주면 *"동의자가 적어서 적게 간 것"* 과 *"다들 꺼서 적게 간 것"* 을 구분할 수 없다.

                    ⚠ **되돌릴 수 없다** — 만들어진 알림은 회수할 수 없다.
                    """)
    ResponseEntity<ApiResponse<MarketingSendResponse>> send(@Valid MarketingSendRequest request);
}
