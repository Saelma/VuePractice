package com.glassvue.global.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * ⚠ <b>기본 프로파일(=운영)에서 메일이 나가지 않는다</b>는 성질을 고정한다 (2026-07-29).
 *
 * <p><b>왜 이 테스트가 생겼나</b>: 처음엔 {@code application.yml} 에 {@code host: ${MAIL_HOST:}} 를 두고
 * "값이 비었으니 발송은 꺼진다"고 <b>적어 두기만</b> 했다. 틀렸다 — 값이 비어도 프로퍼티는 존재해서
 * 자동설정이 {@code JavaMailSender} 를 만들었고, 빈 host 가 localhost 로 폴백해 로컬 메일 캐처(:1025)로
 * <b>실제 메일이 나갔다.</b> 통합 테스트가 보낸 메일이 캐처에 쌓여 있는 걸 보고 알았다.
 *
 * <p>규약이 말하는 그대로다 — <b>"안 나간다"를 주석으로 적지 말고 테스트로 고정한다.</b>
 * 이 테스트가 깨지면 누군가 {@code application.yml} 에 {@code spring.mail} 을 되살린 것이다.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
class MailerAutoConfigOffTest {

    @Autowired Mailer mailer;

    @Test
    @DisplayName("기본 프로파일에서는 발송 채널이 없다 — JavaMailSender 빈이 만들어지지 않는다")
    void defaultProfile_mailDisabled() {
        assertThat(mailer.isEnabled())
                .as("기본(운영) 프로파일에서 메일이 켜져 있으면 안 된다 — application.yml 에 spring.mail 이 되살아났는지 확인할 것")
                .isFalse();
    }
}
