package com.glassvue.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * auth.password-reset.* 설정.
 *
 * <p>exposeToken: 재설정 요청 응답에 토큰을 그대로 담을지 여부. 메일/SMS 발송 인프라가
 * 없는 현 단계에서 dev 프로파일만 true로 켜 화면에서 링크를 바로 확인한다. 운영(기본
 * 프로파일)은 반드시 false — true면 아이디만 알면 누구나 남의 비밀번호를 바꿀 수 있다.
 */
@ConfigurationProperties(prefix = "auth.password-reset")
public record PasswordResetProperties(
        boolean exposeToken,
        long tokenValidityMs
) {
}
