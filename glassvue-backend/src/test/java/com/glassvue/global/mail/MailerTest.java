package com.glassvue.global.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 메일 어댑터 (2026-07-29, B-10 마무리).
 *
 * <p>여기서 고정하는 건 <b>"발송이 호출자를 절대 깨뜨리지 않는다"</b>는 계약이다.
 * 비밀번호 재설정 요청은 <b>열거 방지 때문에 항상 200</b>이어야 하는데, 메일 실패가 예외로 올라오면
 * 500 이 나가면서 "그 아이디는 존재한다"가 드러난다. 그 연결고리를 테스트로 못박는다.
 */
class MailerTest {

    /** ObjectProvider 는 인터페이스라 목으로 만든다 — 빈이 있는/없는 두 상태를 재현하기 위해. */
    @SuppressWarnings("unchecked")
    private static ObjectProvider<JavaMailSender> provider(JavaMailSender sender) {
        ObjectProvider<JavaMailSender> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(sender);
        return p;
    }

    private static MailProperties props() {
        return new MailProperties("no-reply@test.local", "https://example.test/");
    }

    @Test
    @DisplayName("발송 채널이 없으면(운영 기본) 아무 일도 하지 않는다 — 예외도 아니다")
    void disabled_isNoOp() {
        Mailer mailer = new Mailer(provider(null), props());

        assertThat(mailer.isEnabled()).isFalse();
        assertThatCode(() -> mailer.send("a@b.test", "제목", "본문")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("채널이 있으면 수신자·제목·본문·발신자가 그대로 실려 나간다")
    void enabled_sends() {
        JavaMailSender javaMailSender = mock(JavaMailSender.class);
        Mailer mailer = new Mailer(provider(javaMailSender), props());

        mailer.send("user@test.local", "제목", "본문 링크 https://example.test/reset-password?token=abc");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getTo()).containsExactly("user@test.local");
        assertThat(sent.getFrom()).isEqualTo("no-reply@test.local");
        assertThat(sent.getSubject()).isEqualTo("제목");
        assertThat(sent.getText()).contains("token=abc");
    }

    @Test
    @DisplayName("⚠ 발송이 실패해도 예외를 밖으로 내보내지 않는다 (열거 방지가 깨지지 않게)")
    void sendFailure_isSwallowed() {
        JavaMailSender javaMailSender = mock(JavaMailSender.class);
        doThrow(new org.springframework.mail.MailSendException("SMTP down"))
                .when(javaMailSender).send(any(SimpleMailMessage.class));
        Mailer mailer = new Mailer(provider(javaMailSender), props());

        assertThatCode(() -> mailer.send("user@test.local", "제목", "본문"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("base-url 끝의 슬래시는 정리된다 — 링크에 // 가 생기지 않게")
    void baseUrl_trailingSlashTrimmed() {
        assertThat(new MailProperties("f@t.local", "https://example.test/").baseUrl())
                .isEqualTo("https://example.test");
        assertThat(new MailProperties("f@t.local", "https://example.test").baseUrl())
                .isEqualTo("https://example.test");
    }

    @Test
    @DisplayName("빈 설정이면 기본값으로 채운다(널 링크·널 발신자가 나가지 않게)")
    void blankProps_getDefaults() {
        MailProperties p = new MailProperties(null, "  ");
        assertThat(p.from()).isNotBlank();
        assertThat(p.baseUrl()).isNotBlank();
    }

    @Test
    @DisplayName("채널이 없으면 JavaMailSender 를 건드리지도 않는다")
    void disabled_doesNotTouchSender() {
        JavaMailSender javaMailSender = mock(JavaMailSender.class);
        Mailer mailer = new Mailer(provider(null), props());

        mailer.send("a@b.test", "제목", "본문");

        verify(javaMailSender, never()).send(any(SimpleMailMessage.class));
    }
}
