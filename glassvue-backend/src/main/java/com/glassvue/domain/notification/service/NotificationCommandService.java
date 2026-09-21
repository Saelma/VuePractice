package com.glassvue.domain.notification.service;

import com.glassvue.domain.member.service.MemberService;
import com.glassvue.domain.notification.dto.NotificationResponse;
import com.glassvue.domain.notification.entity.Notification;
import com.glassvue.domain.notification.entity.NotificationPref;
import com.glassvue.domain.notification.entity.NotificationType;
import com.glassvue.domain.notification.repository.NotificationPrefRepository;
import com.glassvue.domain.notification.repository.NotificationRepository;
import com.glassvue.domain.notification.sse.NotificationStream;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 생성·읽음·설정 변경 (2026-07-24). 알림을 "만드는" 유일한 입구다 —
 * stub 이던 이벤트 핸들러(주문·재고)가 이걸 호출해 실제 알림함에 쌓고 SSE 로 민다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationCommandService {

    private final NotificationRepository notificationRepository;
    private final NotificationPrefRepository prefRepository;
    private final NotificationStream stream;
    private final MemberService memberService;

    /**
     * 알림 한 건 생성 + 실시간 푸시. 설정에서 그 타입을 껐으면 <b>만들지 않는다</b>(opt-out).
     *
     * <p>저장 뒤 SSE 로 미는데, 페이로드가 알림 전체라 화면은 재조회 없이 목록·토스트에 바로 넣는다.
     * 푸시는 best-effort — 실패해도(끊긴 연결 등) 알림은 DB 에 남아 재조회 때 보인다.
     *
     * <p>🔴 <b>받을 사람이 없으면 만들지 않는다</b> (2026-09-21, BACKLOG §F-9). 탈퇴는 하드 삭제인데
     * 주문·리뷰는 남으므로(F-1), <b>탈퇴 «뒤» 에</b> 관리자가 그 주문을 취소하면 <b>아무도 영원히 못 읽는
     * 알림</b>이 생겼다(2026-09-21 실측 — 불변식 ⑬ 이 그날 처음 1 이 됐다).
     * ⚠ 기존 방어 셋은 전부 <b>«탈퇴 시점»</b> 을 본다(탈퇴 리스너 · 보관 배치 · {@code ProductPurgedEvent}) —
     * <b>그 뒤에 생기는 것</b>은 아무도 안 막았다. 그래서 «정리» 를 하나 더 얹는 것이 답이 아니다.
     * 🔴 <b>여기가 «알림을 만드는 유일한 입구» 라 한 곳으로 족하다</b> — 발행 자리(주문·반품·재고…)마다
     * 막으면 «손으로 늘리는 목록» 이 되어 반드시 빠진다(WA §2-12).
     * ⚠ <b>정보는 안 잃는다</b> — 그 조작은 {@code admin_audit_log} 에 남는다. 알림의 목적은 «전달» 이고
     * «기록» 은 원장이 한다. <b>받을 사람이 없으면 목적이 없다.</b>
     * ⚠ <b>비용</b>: 알림 한 건마다 {@code existsById} 가 한 번 더 돈다(PK 조회라 가볍다).
     *
     * @return <b>실제로 만들었으면</b> {@code true}, <b>안 만들었으면</b> {@code false}
     *         (설정에서 꺼져 있거나, <b>받을 회원이 없거나</b>).
     *         ⚠ 이 반환값은 <b>마케팅 발송(B-21 후속)이 "몇 명에게 갔는지" 를 정직하게 세기 위해</b>
     *         생겼다. 대상 수만 세고 발송 결과를 안 세면 <b>토글을 끈 사람까지 "보냈다"로 보고</b>하게 된다.
     *         기존 호출부(이벤트 핸들러들)는 반환값을 쓰지 않는다 — 무시해도 무해하다.
     */
    @Transactional
    public boolean create(UUID memberId, NotificationType type, String title, String message, String link) {
        // 🔴 **설정보다 먼저 본다** — 없는 회원의 설정을 묻는 것은 뜻이 없다.
        if (!memberService.exists(memberId)) {
            log.info("[알림] 받을 회원이 없어 만들지 않는다 — member={} type={}", memberId, type);
            return false;
        }
        boolean enabled = prefRepository.findByMemberIdAndType(memberId, type)
                .map(NotificationPref::isEnabled)
                .orElse(true); // 행이 없으면 켜짐(기본 on)
        if (!enabled) {
            return false;
        }
        Notification saved = notificationRepository.save(Notification.of(memberId, type, title, message, link));
        stream.push(memberId, NotificationResponse.from(saved));
        return true;
    }

    /** 읽음 처리 — 본인 알림만. 멱등(이미 읽었어도 정상). */
    @Transactional
    public void markRead(UUID id, UUID memberId) {
        Notification notification = notificationRepository.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markRead();
    }

    /** 모두 읽음. 안읽은 것만 벌크 UPDATE. */
    @Transactional
    public void markAllRead(UUID memberId) {
        notificationRepository.markAllRead(memberId);
    }

    /**
     * 알림 타입 켜기/끄기 — 없으면 만들고 있으면 바꾼다(upsert).
     *
     * <p><b>UPDATE 먼저, 없으면 INSERT.</b> find→insert 로 하면 같은 (member,type)의 빠른 연속 토글이
     * 둘 다 "없음"으로 읽고 각자 INSERT 해 유니크 제약(ORA-00001)에 걸린다(2026-07-24 실측).
     * 재토글은 순수 UPDATE 라 그 경합이 사라진다. 화면도 요청 중 토글을 잠가 최초 동시삽입까지 막는다.
     */
    @Transactional
    public void changeSetting(UUID memberId, NotificationType type, boolean enabled) {
        int updated = prefRepository.updateEnabled(memberId, type, enabled);
        if (updated == 0) {
            prefRepository.save(NotificationPref.of(memberId, type, enabled));
        }
    }

    /**
     * 회원 삭제 정리(F-1) — 알림과 <b>알림 설정</b>을 함께 지운다.
     *
     * <p>⚠ 설정({@code notification_pref})은 백로그의 F-1 목록에 없었다 — 회원별 행인데 빠져 있었다.
     * "회원 id 를 들고 있는 엔티티"를 코드에서 전수로 뽑아야 보이는 자리다(2026-07-30).
     */
    @Transactional
    public void deleteAllForMember(UUID memberId) {
        long notifications = notificationRepository.deleteByMemberId(memberId);
        long prefs = prefRepository.deleteByMemberId(memberId);
        log.info("Notifications deleted for member {}: notifications={} prefs={}", memberId, notifications, prefs);
    }
}
