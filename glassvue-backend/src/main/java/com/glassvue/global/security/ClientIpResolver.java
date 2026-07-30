package com.glassvue.global.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 요청의 실제 클라이언트 IP — nginx 뒤에 있으므로 그냥 {@code getRemoteAddr()} 을 쓰면 안 된다.
 *
 * <p>⚠ <b>이걸 안 만들면 IP 기준 제한이 정반대로 동작한다</b>(2026-07-30 실측): 이 앱은
 * {@code server.forward-headers-strategy} 를 설정하지 않았고 기본값은 {@code NONE} 이라,
 * {@code getRemoteAddr()} 은 <b>모든 요청에서 nginx 주소(127.0.0.1)</b> 를 돌려준다.
 * 그 상태로 "IP당 20회" 를 걸면 한 사람이 20번 틀리는 순간 <b>전 사용자가 함께 잠긴다.</b>
 *
 * <p>⚠ <b>{@code X-Forwarded-For} 가 아니라 {@code X-Real-IP} 를 쓴다.</b> 우리 nginx 설정은
 * {@code X-Forwarded-For $proxy_add_x_forwarded_for} 인데, 그건 <b>클라이언트가 보낸 값에 덧붙이는</b>
 * 방식이라 <b>첫 값을 공격자가 심을 수 있다</b>(스푸핑 → IP 제한 우회). 반면
 * {@code X-Real-IP $remote_addr} 는 nginx 가 <b>항상 덮어쓰므로</b> 위조가 안 된다.
 *
 * <p>헤더가 없으면(= nginx 를 거치지 않은 직접 접근, 예: 서버 안에서 {@code :8080} 호출)
 * {@code getRemoteAddr()} 로 떨어진다. 그 경로로는 헤더를 마음대로 넣을 수 있지만,
 * <b>아이디 기준 제한은 그대로 걸리므로</b> 무차별 대입 방어가 통째로 무력화되지는 않는다.
 */
public final class ClientIpResolver {

    private static final String REAL_IP_HEADER = "X-Real-IP";

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String realIp = request.getHeader(REAL_IP_HEADER);
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }
}
