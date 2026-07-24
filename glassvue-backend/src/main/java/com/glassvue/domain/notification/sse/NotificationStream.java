package com.glassvue.domain.notification.sse;

import com.glassvue.domain.notification.dto.NotificationResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 연결 레지스트리 (2026-07-24). 회원별로 열린 스트림을 들고, 알림이 생기면 밀어 준다.
 *
 * <p><b>왜 SSE 인가</b>: 인앱 알림은 사이트를 보고 있는 동안 즉시 떠야 한다. FCM/웹푸시는 탭이 닫혀도
 * 보내려는 것이라 외부 서비스(Firebase)·서비스워커가 필요해 우리 "외부 의존 없음/LAN" 원칙과 안 맞는다.
 * SSE 는 서버→브라우저 단방향 스트림 하나로 충분하고 자체 호스팅된다(WebSocket 은 양방향이라 과하다).
 *
 * <p>⚠ 배포 시 nginx 가 이 경로를 <b>버퍼링하지 않게</b> 해야 이벤트가 즉시 나간다
 * ({@code proxy_buffering off} + 긴 {@code proxy_read_timeout}). 안 하면 알림이 뭉쳐 있다가 늦게 온다.
 *
 * <p>인프로세스 best-effort 다 — 서버가 죽으면 그동안의 실시간 푸시는 사라지지만, 알림 자체는 DB 에
 * 남아 재연결·재조회 때 보인다. (유실 금지 보장은 MSA 단계의 메시지 브로커 몫.)
 */
@Slf4j
@Component
public class NotificationStream {

    /** 클라이언트가 이 시간마다 재연결한다(브라우저·프록시가 오래된 연결을 끊기 전에 새로 맺게). */
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<UUID, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /** 한 회원의 새 스트림을 등록한다. 한 사람이 여러 탭을 열 수 있어 회원당 여러 emitter 를 허용한다. */
    public SseEmitter subscribe(UUID memberId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.computeIfAbsent(memberId, k -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(memberId, emitter));
        emitter.onTimeout(emitter::complete); // 완료 콜백이 이어서 정리한다
        emitter.onError(e -> remove(memberId, emitter));
        try {
            // 첫 이벤트 — 연결이 실제로 열렸음을 클라가 알게(프록시 버퍼가 첫 바이트를 흘려보내는지도 여기서 드러난다).
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            remove(memberId, emitter);
        }
        return emitter;
    }

    /** 한 회원에게 새 알림을 민다. 끊긴 연결은 조용히 정리한다(best-effort). */
    public void push(UUID memberId, NotificationResponse payload) {
        Set<SseEmitter> set = emitters.get(memberId);
        if (set == null) {
            return; // 그 회원이 접속 중이 아니면 DB 에만 남는다(재조회 때 보인다).
        }
        for (SseEmitter emitter : set) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(payload));
            } catch (Exception e) {
                remove(memberId, emitter);
            }
        }
    }

    /**
     * 15초마다 하트비트(주석 라인) — nginx {@code proxy_read_timeout} 이나 브라우저가 유휴 연결을
     * 끊지 않게 살려 둔다. 보내다 실패하면 이미 끊긴 연결이라 정리한다.
     */
    @Scheduled(fixedRate = 15_000)
    public void heartbeat() {
        emitters.forEach((memberId, set) -> {
            for (SseEmitter emitter : set) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (Exception e) {
                    remove(memberId, emitter);
                }
            }
        });
    }

    private void remove(UUID memberId, SseEmitter emitter) {
        Set<SseEmitter> set = emitters.get(memberId);
        if (set != null) {
            set.remove(emitter);
            if (set.isEmpty()) {
                emitters.remove(memberId);
            }
        }
    }
}
