package com.glassvue.global.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * <b>날짜(KST) → 시각(UTC) 경계를 만드는 한 곳.</b>
 *
 * <p>API 는 {@code LocalDate} 만 받고 <b>경계는 서버가 만든다</b>(B-26). 화면이 {@code Instant} 를
 * 보내기 시작하면 «그 날의 00:00 이 언제인가» 가 <b>두 곳</b>에서 계산되고, 🔴 <b>하루가 어긋나도
 * 화면은 멀쩡해 보인다.</b>
 *
 * <p>⚠ <b>«종료일 포함» 을 다음 날 00:00 <u>미만</u>으로 옮긴다.</b> {@code 23:59:59} 로 자르면
 * 그 날 {@code 23:59:59.5} 에 생긴 행이 빠지는데, <b>초 미만은 눈에 안 보여 더 나쁘다</b>(B-26).
 *
 * <p>🔴 <b>왜 «설정» 이 아니라 상수인가</b>: {@code BACKLOG F-4} 가 «Asia/Seoul 이 5곳에 박혀 있고
 * 지금은 그게 맞는 동작이다 — 흩어진 상수를 한 곳(설정)으로 모으는 건 시간대가 늘어날 때 하면 된다»
 * 고 판단해 뒀다. 여기서 모으는 것은 <b>«상수» 가 아니라 «규칙»</b> 이다 —
 * 같은 경계 계산이 {@code Transform}·{@code OrderStatsQueryService}·감사 조회 <b>세 곳</b>에
 * 따로 있으면 한쪽만 고쳐진다.
 *
 * <p>⚠ <b>{@code ZoneId.systemDefault()} 를 쓰지 않는다.</b> 2026-09-07 까지 {@code Transform} 이
 * 그걸 썼고, 서버 시간대가 {@code Asia/Seoul} 이라 <b>결과가 우연히 맞았다.</b> 🔴 JVM 시간대는
 * 컨테이너·systemd·기동 옵션으로 바뀔 수 있고, 바뀌면 <b>공지·주문 날짜 필터만 조용히 어긋난다</b>
 * (매출은 KST 를 직접 박아 두어 안 움직인다 — 즉 <b>두 화면이 서로 다른 하루를 보게 된다</b>).
 */
public final class KstDates {

    /** 장부의 하루가 시작하는 곳. ⚠ 저장은 UTC({@code Instant}) 그대로다 — 경계만 KST 다. */
    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private KstDates() {
    }

    /** 그 날 00:00 (KST) — 기간의 <b>포함</b> 하한. */
    public static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(KST).toInstant();
    }

    /** 다음 날 00:00 (KST) — 기간의 <b>배타</b> 상한. 즉 {@code date} 는 기간에 <b>포함된다.</b> */
    public static Instant startOfNextDay(LocalDate date) {
        return date.plusDays(1).atStartOfDay(KST).toInstant();
    }

    /** 지금 KST 기준의 «오늘». ⚠ 프리셋(«이번 달» 등)의 기준점은 <b>브라우저 시계가 아니라 이것</b>이다. */
    public static LocalDate today() {
        return LocalDate.now(KST);
    }
}
