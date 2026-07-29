package com.glassvue.global.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 메일 발송 어댑터 (2026-07-29, B-10 마무리).
 *
 * <p>⚠ <b>이름이 {@code MailSender} 가 아닌 이유</b>: 그렇게 두면 빈 이름이 {@code mailSender} 가 되어
 * 스프링 부트가 자동 등록하는 {@code JavaMailSenderImpl}(빈 이름도 {@code mailSender})과 충돌해
 * {@code BeanDefinitionOverrideException} 으로 <b>컨텍스트가 통째로 안 뜬다</b>(실제로 겪었다).
 * 스프링이 이미 쓰는 이름은 피한다.
 *
 * <p><b>왜 global 인가</b> — 발송은 업무가 아니라 인프라다. 지금은 auth(비밀번호 재설정)만 쓰지만
 * 주문 확인·배송 알림도 같은 통로를 쓰게 된다. 도메인에 두면 그 도메인에 종속돼 버린다.
 *
 * <p><b>⚠ 운영에서 꺼지는 것을 "설정 플래그"가 아니라 "빈 부재"로 보장한다.</b>
 * {@code spring.mail.host} 프로퍼티가 <b>아예 없으면</b> 스프링이 {@link JavaMailSender} 빈을 만들지 않는다.
 * 그래서 {@link ObjectProvider} 가 비고, 이 클래스는 조용히 no-op 이 된다.
 *
 * <p>⚠ <b>"기본값을 비워 둔다"로는 안 막힌다</b>(2026-07-29 실측). 처음엔 {@code host: ${MAIL_HOST:}} 로
 * 뒀는데, 값이 비어도 <b>프로퍼티는 존재</b>해서 자동설정이 빈을 만들었고 빈 host 가 localhost 로 폴백해
 * {@code :1025} 에 붙었다 — 기본 프로파일로 도는 <b>통합 테스트가 실제로 메일을 보내</b> 드러났다.
 * 그래서 {@code application.yml} 에는 {@code spring.mail} 키 자체를 두지 않는다.
 * ({@code MailerAutoConfigOffTest} 가 이 성질을 고정한다.)
 *
 * <p><b>발송 실패가 호출자를 깨뜨리지 않는다.</b> 비밀번호 재설정은 <b>메일이 안 가도 토큰은 이미
 * 발급된 상태</b>이고, 여기서 예외를 던지면 컨트롤러가 500 을 내면서 <b>"그 아이디가 존재한다"는 사실이
 * 새어 나간다</b>(열거 방지가 깨진다). 그래서 잡아서 로그만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Mailer {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final MailProperties props;

    /** 발송 가능 여부 — 화면·테스트가 "지금 메일이 나가는 환경인가"를 물을 때. */
    public boolean isEnabled() {
        return mailSender.getIfAvailable() != null;
    }

    /**
     * 텍스트 메일 한 통. 발송 채널이 없으면 <b>아무 일도 하지 않는다</b>(예외 아님).
     *
     * <p>⚠ 본문·수신자를 로그에 남기지 않는다 — 개인정보이고, 재설정 링크는 <b>그 자체가 자격증명</b>이라
     * 로그에 찍히면 로그를 읽을 수 있는 사람이 남의 비밀번호를 바꿀 수 있다.
     */
    public void send(String to, String subject, String body) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.debug("Mail channel disabled — skipped (subject={})", subject);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(props.from());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
            log.info("Mail sent (subject={})", subject); // 수신자·본문은 남기지 않는다
        } catch (Exception e) {
            // 위 주석 참조 — 던지면 열거 방지가 깨진다. 발송은 best-effort 다.
            log.warn("Mail send failed (subject={}): {}", subject, e.toString());
        }
    }
}
