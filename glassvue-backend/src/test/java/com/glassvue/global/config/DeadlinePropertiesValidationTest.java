package com.glassvue.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.glassvue.domain.catalog.config.CatalogProperties;
import com.glassvue.domain.image.config.ImageProperties;
import com.glassvue.domain.notification.config.NotificationProperties;
import com.glassvue.domain.order.config.OrderProperties;
import com.glassvue.global.security.JwtProperties;
import com.glassvue.global.security.PasswordResetProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * <b>기한 설정값을 기동할 때 막는다</b> (2026-09-10 · BACKLOG §P).
 *
 * <p>🔴 <b>그전에는 주석으로만 막혀 있었다.</b> {@code application.yml} 이
 * *«기한을 없애고 싶으면 크게 두지, 0 으로 두지 않는다 — 0 이면 배송완료 즉시 막힌다»* 라고
 * 적어 뒀는데, <b>0 을 넣어도 아무 일도 안 일어났다</b> — 조용히 뜨고, 그 뒤로 조용히 이상하게 돌았다.
 * ⚠ 2026-09-10 실측: {@code @ConfigurationProperties} 레코드 <b>10개 전부 검증 애노테이션 0개</b>.
 *
 * <p>⚠ <b>«기한이 서로 맞물리나» 를 보러 갔다가 «기한을 누가 지키나» 로 왔다.</b> 상호작용은 깨끗했다
 * (purge↔반품·배치주기↔유예·화면↔서버 셋 다 이미 처리돼 있다). 🔴 <b>비어 있던 것은 값 자체의 가드다.</b>
 *
 * <p>⚠ <b>Boot 4 에서 {@code ValidationAutoConfiguration} 의 패키지가 옮겨졌다</b> —
 * {@code org.springframework.boot.validation.autoconfigure} 다(옛 {@code ...autoconfigure.validation} 아님).
 *
 * <p>⚠ <b>가장 비싼 자리는 {@code catalog.purge-grace-days} 다</b> — 0 이면 「삭제 대기」가
 * <b>즉시 영구 삭제</b>되어 F-7 이 만든 안전망이 오타 하나로 사라진다. <b>되돌릴 수 없다.</b>
 */
class DeadlinePropertiesValidationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class))
            .withUserConfiguration(Props.class);

    @Configuration
    @EnableConfigurationProperties({OrderProperties.class, CatalogProperties.class,
            NotificationProperties.class, ImageProperties.class,
            JwtProperties.class, PasswordResetProperties.class})
    static class Props {
    }

    /** 운영과 같은 값 — 이게 안 뜨면 아래 시험들이 «막았다» 가 아니라 «원래 안 뜬다» 가 된다. */
    private static final String[] VALID = {
            "order.return-grace-days=7",
            "catalog.low-stock-threshold=5", "catalog.purge-grace-days=7", "catalog.purge-enabled=true",
            "notification.read-retention-days=30", "notification.unread-retention-days=180",
            "notification.cleanup-enabled=true",
            "image.cleanup-enabled=true", "image.cleanup-grace-hours=24",
            "jwt.secret=dGVzdC1zZWNyZXQ=", "jwt.access-token-validity-ms=1800000",
            "jwt.refresh-token-validity-ms=1209600000",
            "auth.password-reset.expose-token=false", "auth.password-reset.token-validity-ms=1800000",
    };

    private String[] withOverride(String... overrides) {
        String[] all = new String[VALID.length + overrides.length];
        System.arraycopy(VALID, 0, all, 0, VALID.length);
        System.arraycopy(overrides, 0, all, VALID.length, overrides.length);
        return all;
    }

    @Test
    @DisplayName("🔴 대조군 먼저 — 운영과 같은 값이면 뜬다 (안 그러면 아래가 «막았다» 가 아니다)")
    void validValuesBind() {
        runner.withPropertyValues(VALID).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(CatalogProperties.class).purgeGraceDays()).isEqualTo(7);
            assertThat(ctx.getBean(NotificationProperties.class).unreadRetentionDays()).isEqualTo(180);
        });
    }

    @Test
    @DisplayName("🔴 catalog.purge-grace-days=0 이면 기동이 막힌다 — 되돌릴 수 없는 삭제가 즉시 일어날 값이다")
    void purgeGraceZeroIsRejected() {
        runner.withPropertyValues(withOverride("catalog.purge-grace-days=0"))
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    @DisplayName("반품 기한 0 이면 막힌다 — 주석이 «0 으로 두지 않는다» 고만 적어 두던 자리다")
    void returnGraceZeroIsRejected() {
        runner.withPropertyValues(withOverride("order.return-grace-days=0"))
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    @DisplayName("알림 보관 0 이면 막힌다 — 읽는 즉시 사라지는 알림함이 된다")
    void notificationRetentionZeroIsRejected() {
        runner.withPropertyValues(withOverride("notification.read-retention-days=0"))
                .run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues(withOverride("notification.unread-retention-days=0"))
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    @DisplayName("🔴 안 읽은 것을 읽은 것보다 짧게 둘 수 없다 — 범위가 아니라 «둘 사이» 의 제약이다")
    void unreadShorterThanReadIsRejected() {
        runner.withPropertyValues(withOverride(
                        "notification.read-retention-days=60", "notification.unread-retention-days=30"))
                .run(ctx -> assertThat(ctx).hasFailed());

        // ⚠ 대조군 — 같기만 해도 통과한다(«짧을 수 없다» 지 «길어야 한다» 가 아니다).
        runner.withPropertyValues(withOverride(
                        "notification.read-retention-days=30", "notification.unread-retention-days=30"))
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    @DisplayName("이미지 유예 0 이면 막힌다 — 작성 중인 폼의 이미지를 뺏는 값이다")
    void imageGraceZeroIsRejected() {
        runner.withPropertyValues(withOverride("image.cleanup-grace-hours=0"))
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    @DisplayName("토큰 유효시간 0 · 빈 secret 이면 막힌다")
    void jwtInvalidIsRejected() {
        runner.withPropertyValues(withOverride("jwt.access-token-validity-ms=0"))
                .run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues(withOverride("jwt.secret="))
                .run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues(withOverride("auth.password-reset.token-validity-ms=0"))
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    @DisplayName("⚠ 재고 임계값 0 은 «막지 않는다» — 여기선 0 이 「품절도 포함」이라는 뜻이다")
    void lowStockThresholdZeroIsAllowed() {
        runner.withPropertyValues(withOverride("catalog.low-stock-threshold=0"))
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }
}
