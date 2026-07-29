package com.glassvue.global.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 메일 발송 부가 설정 ({@code glassvue.mail}).
 *
 * <p>SMTP 접속 정보(host·port)는 스프링 표준 {@code spring.mail.*} 이 갖는다 — 그쪽을 재정의하지 않는다.
 * 여기 있는 건 <b>우리가 정하는 것</b> 둘뿐이다.
 *
 * @param from    보내는 사람 주소. 로컬 캐처는 아무 값이나 받지만, 실제 SMTP 는 도메인 검증을 한다.
 * @param baseUrl 메일 본문의 링크가 가리킬 곳. ⚠ <b>서버가 자기 주소를 알 방법이 없어</b> 설정으로 받는다 —
 *                요청 헤더({@code Host})로 만들면 <b>공격자가 헤더를 조작해 자기 서버로 가는 재설정 링크를
 *                보내게</b> 할 수 있다(host header injection). 그래서 요청이 아니라 설정에서 온다.
 */
@ConfigurationProperties(prefix = "glassvue.mail")
public record MailProperties(String from, String baseUrl) {

    public MailProperties {
        from = (from == null || from.isBlank()) ? "no-reply@glassvue.local" : from;
        baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "http://localhost:3000" : baseUrl;
        // 링크를 조립할 때 슬래시가 겹치지 않게 끝을 정리한다.
        baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
