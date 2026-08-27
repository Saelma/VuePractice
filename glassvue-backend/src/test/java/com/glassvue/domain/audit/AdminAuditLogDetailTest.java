package com.glassvue.domain.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.glassvue.domain.audit.entity.AdminAuditLog;
import com.glassvue.domain.audit.entity.AuditAction;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 🔴 <b>원장 {@code detail} 의 상한</b> (2026-08-27, BACKLOG §I-13) — 순수 단위 테스트.
 *
 * <p><b>왜 이 방어가 필요한가</b>: 감사는 발행측 트랜잭션에 <b>합류</b>한다. detail 이 컬럼을 넘기면
 * {@code ORA-12899} 로 <b>조작 전체가 롤백된다</b> — 즉 빠뜨림의 대가가 «감사가 안 남는다» 가 아니라
 * <b>«기능이 안 된다»</b> 다({@code AuditActionTargetTypeTest} 가 지키는 것과 같은 성질).
 *
 * <p>🔴 <b>이제 여기가 유일한 방어선이다.</b> 전엔 도메인마다 각자 잘랐고 <b>방식이 서로 달랐다</b> —
 * 주문은 «…(잘림)» 을 붙였고 상품은 <b>조용히</b> 잘랐다. 둘을 걷어내고 이 클래스 하나로 모았으므로,
 * 이 파일이 무너지면 <b>9개 도메인이 한꺼번에</b> 무방비가 된다.
 *
 * <p>⚠ DB 컬럼과의 대조는 여기서 못 한다(DB 를 안 띄운다) — {@code @Column(length = DETAIL_MAX)} 가
 * 같은 상수를 쓰므로 스키마 검증({@code ddl-auto=validate})이 그쪽을 지킨다.
 */
class AdminAuditLogDetailTest {

    private AdminAuditLog log(String detail) {
        return AdminAuditLog.builder()
                .action(AuditAction.ORDER_RETURN_APPROVE)
                .actorId(UUID.randomUUID()).actorName("ZZ관리자")
                .targetId(UUID.randomUUID()).targetLogin("zzbuyer")
                .detail(detail)
                .build();
    }

    private static String repeat(char c, int n) {
        return String.valueOf(c).repeat(n);
    }

    @Test
    @DisplayName("상한 안이면 그대로 둔다 — 멀쩡한 원장에 손대지 않는다")
    void keepsShortDetailUntouched() {
        String detail = "20260827-0001 / 사유: 단순 변심 / 지바 1개 반품";

        assertThat(log(detail).getDetail()).isEqualTo(detail);
    }

    @Test
    @DisplayName("경계 — 정확히 상한이면 안 자른다")
    void keepsExactlyMaxLength() {
        String detail = repeat('가', AdminAuditLog.DETAIL_MAX);

        assertThat(log(detail).getDetail()).isEqualTo(detail);
        assertThat(log(detail).getDetail()).hasSize(AdminAuditLog.DETAIL_MAX);
    }

    @Test
    @DisplayName("🔴 상한을 넘기면 자르되 «잘렸다» 고 말한다 — 조용히 자르면 그게 전부인 줄 안다")
    void truncatesAndSaysSo() {
        String detail = repeat('가', AdminAuditLog.DETAIL_MAX + 500);

        String stored = log(detail).getDetail();

        // 🔴 **상한을 넘지 않는다** — 이걸 어기면 ORA-12899 로 조작이 롤백된다.
        assertThat(stored).hasSize(AdminAuditLog.DETAIL_MAX);
        assertThat(stored).endsWith("…(잘림)");
    }

    @Test
    @DisplayName("🔴 잘림 표시가 «상한 안» 에 들어간다 — 표시를 덧붙여 넘기면 방어가 스스로 깨진다")
    void truncationMarkFitsInsideTheLimit() {
        // ⚠ 「자르고 나서 표시를 붙인다」로 쓰면 표시 길이만큼 다시 넘친다 — 흔한 실수라 못 박는다.
        for (int over : new int[] {1, 2, 7, 100, 5_000}) {
            String stored = log(repeat('나', AdminAuditLog.DETAIL_MAX + over)).getDetail();
            assertThat(stored.length())
                    .as("초과 %d 자", over)
                    .isLessThanOrEqualTo(AdminAuditLog.DETAIL_MAX);
        }
    }

    @Test
    @DisplayName("🔴 서로게이트 쌍 한가운데를 자르지 않는다 — 이모지가 들어오면 실제로 걸린다")
    void doesNotSplitSurrogatePairs() {
        // 😀 는 Java 에서 char 둘이다. 앞을 홀수로 채워 **잘리는 자리가 쌍 한가운데** 오게 만든다.
        String mark = "…(잘림)";
        int keep = AdminAuditLog.DETAIL_MAX - mark.length();
        String detail = repeat('가', keep - 1) + "😀".repeat(200);

        String stored = log(detail).getDetail();

        assertThat(stored.length()).isLessThanOrEqualTo(AdminAuditLog.DETAIL_MAX);
        // 짝 없는 서로게이트가 남으면 Oracle 이 거부하거나 깨진 글자가 저장된다.
        String body = stored.substring(0, stored.length() - mark.length());
        assertThat(body.isEmpty() || !Character.isHighSurrogate(body.charAt(body.length() - 1)))
                .as("마지막 글자가 짝 없는 high surrogate 면 안 된다")
                .isTrue();
    }

    @Test
    @DisplayName("null 은 그대로 null — 정지·해제처럼 detail 이 없는 조작이 있다")
    void keepsNull() {
        assertThat(log(null).getDetail()).isNull();
    }
}
