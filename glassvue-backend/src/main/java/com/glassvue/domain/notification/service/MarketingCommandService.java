package com.glassvue.domain.notification.service;

import com.glassvue.domain.member.service.MemberService;
import com.glassvue.domain.notification.dto.MarketingSendRequest;
import com.glassvue.domain.notification.dto.MarketingSendResponse;
import com.glassvue.domain.notification.entity.NotificationType;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마케팅 알림 발송 (2026-08-03, B-21 후속).
 *
 * <p><b>지금까지의 알림과 근본적으로 다른 점</b>: 나머지 넷(주문·재고·재입고·문의)은 전부
 * <b>이벤트에 반응해 자동 생성</b>되는데, 마케팅은 <b>관리자가 내용을 써서 보내는</b> 첫 알림이다.
 * 그래서 이벤트 3층 구조를 쓰지 않는다 — 반응할 도메인 사건이 없고, 관리자의 요청 자체가 시작점이다.
 *
 * <p><b>도메인 경계</b>: 대상 선정의 근거({@code marketing_agreed_at})는 member 가 갖고 있으므로
 * {@link MemberService#marketingAgreedIds()} <b>공개 API 로만</b> 가져온다 —
 * {@code StockAlertHandler} 가 {@code adminIds()} 를 쓰는 것과 같은 자리다.
 * notification 은 member 테이블을 직접 만지지 않는다.
 *
 * <p>⚠ <b>두 조건을 두 주체가 나눠 본다</b>(설계 결정, 2026-08-03):
 * <ul>
 *   <li><b>동의했나</b>(근거) → member 가 판단해서 목록을 준다</li>
 *   <li><b>지금 받고 싶나</b>(선호) → {@link NotificationCommandService#create} 가 판단해서 거른다</li>
 * </ul>
 * 합치지 않은 이유는 <b>토글을 끌 때 동의 기록이 지워지면 안 되기 때문</b>이다 — 그러면
 * "이 사람이 언제 동의했었나"에 영영 답할 수 없다. 둘은 뜻이 다른 값이라 저장소도 다르다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingCommandService {

    private final MemberService memberService;
    private final NotificationCommandService notificationService;

    /**
     * 동의자 전원에게 마케팅 알림을 만든다. 수신을 끈 사람은 {@code create} 가 걸러 낸다.
     *
     * <p>⚠ <b>보낸 수를 `create` 의 반환값으로 센다</b> — 대상 수를 그대로 "보냈다"고 보고하면
     * <b>토글을 끈 사람까지 발송으로 집계</b>된다. 관리자가 보는 숫자가 거짓이 되는 자리다.
     *
     * <p>⚠ 지금 규모(회원 수십 명)에선 단순 루프로 충분하다. 회원이 늘면 <b>알림 행이 회원 수만큼</b>
     * 생기므로 배치·페이징이 필요해진다 — 그때 손대면 되고, 지금 미리 만들지 않는다(§1).
     */
    @Transactional
    public MarketingSendResponse send(MarketingSendRequest req) {
        List<UUID> agreed = memberService.marketingAgreedIds();
        String link = (req.link() == null || req.link().isBlank()) ? null : req.link().trim();

        int sent = 0;
        for (UUID memberId : agreed) {
            if (notificationService.create(
                    memberId, NotificationType.MARKETING, req.title(), req.message(), link)) {
                sent++;
            }
        }
        log.info("[마케팅] 발송 title='{}' 동의={} 발송={} 수신거부={}",
                req.title(), agreed.size(), sent, agreed.size() - sent);
        return MarketingSendResponse.of(agreed.size(), sent);
    }

    /**
     * 발송 <b>전에</b> 대상이 몇 명인지 보여 준다 — 화면이 "몇 명에게 갑니다"를 먼저 말할 수 있게.
     *
     * <p>⚠ 되돌릴 수 없는 조작이라(알림은 회수할 수 없다) <b>누르기 전에 규모를 아는 것</b>이
     * 실수를 막는 유일한 수단이다. ⚠ 다만 이 숫자는 <b>동의자 수</b>이고 실제 발송은 그보다 적을 수
     * 있다(수신 거부) — 화면 문구가 그걸 단정하지 않아야 한다.
     */
    @Transactional(readOnly = true)
    public int audienceSize() {
        return memberService.marketingAgreedIds().size();
    }
}
