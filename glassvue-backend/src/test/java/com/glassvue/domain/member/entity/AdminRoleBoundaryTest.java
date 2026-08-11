package com.glassvue.domain.member.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.glassvue.global.security.AuthUser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * <b>「관리자 이상」의 경계</b> — {@link Role#isAdmin()} · {@link AuthUser#isAdmin()} 과
 * 그 경계가 <b>다시 흩어지지 않는지</b>를 함께 지킨다 (2026-08-11, 2026-08-10 §16-3 후속).
 *
 * <p><b>왜 이 테스트가 따로 있나.</b> 사고는 «어느 한 서비스가 틀렸다» 가 아니라
 * <b>«같은 판단이 여섯 군데에 복사돼 있었다»</b> 였다. 각 서비스의 테스트를 아무리 잘 써도
 * <b>일곱 번째가 새로 생기는 것</b>은 못 막는다 — 그건 자리마다의 문제가 아니라 모양의 문제라서다.
 * 그래서 여기서 <b>소스를 직접 훑는다.</b>
 *
 * <p>⚠ WORKING-AGREEMENTS §2-3 이 이미 «권한 판단은 프론트와 백엔드 기준을 일치시킨다» 인데도
 * 2026-07-28 ~ 08-11 동안 어긋나 있었다. <b>필요한 것은 규약이 아니라 강제였다</b>(2026-08-10 §16-3-1).
 */
class AdminRoleBoundaryTest {

    // ── 판정 함수 ────────────────────────────────────────────────────────────

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMIN", "SUPER_ADMIN"})
    @DisplayName("ADMIN · SUPER_ADMIN 은 관리자 이상이다")
    void adminRoles_areAdmin(Role role) {
        assertThat(role.isAdmin()).isTrue();
        assertThat(new AuthUser(UUID.randomUUID(), role, "관리자").isAdmin()).isTrue();
    }

    @Test
    @DisplayName("USER 는 관리자가 아니다 — 경계가 통째로 참이 되지 않았음을 함께 못 박는다")
    void user_isNotAdmin() {
        assertThat(Role.USER.isAdmin()).isFalse();
        assertThat(new AuthUser(UUID.randomUUID(), Role.USER, "손님").isAdmin()).isFalse();
    }

    /**
     * 역할을 새로 만들면 여기서 걸린다 — 「관리자 이상인가」에 답을 안 정한 역할이 조용히 USER 쪽으로
     * 떨어지는 것을 막는다. 지금 셋 중 관리자는 둘이고, 그 둘이 {@link Role#adminRoles()} 와 같아야 한다.
     */
    @Test
    @DisplayName("adminRoles() 는 isAdmin() 인 역할 전부와 정확히 같다 (쿼리용 경계 ↔ 비교용 경계)")
    void adminRoles_matchesIsAdmin() {
        List<Role> byPredicate = Stream.of(Role.values()).filter(Role::isAdmin).toList();
        assertThat(Role.adminRoles())
                .as("한쪽만 늘면 «알림은 안 오는데 버튼은 눌린다» 처럼 갈린다")
                .containsExactlyInAnyOrderElementsOf(byPredicate);
    }

    /** JWT 에 역할이 없던 토큰 등 — {@code AuthUser.isAdmin()} 이 NPE 로 터지지 않는다. */
    @Test
    @DisplayName("role 이 null 인 principal 은 관리자가 아니다 (터지지 않는다)")
    void nullRole_isNotAdmin() {
        assertThat(new AuthUser(UUID.randomUUID(), null, "?").isAdmin()).isFalse();
    }

    // ── 흩어짐 방지 ──────────────────────────────────────────────────────────

    /**
     * 🔴 <b>이 테스트가 이 파일의 본론이다.</b>
     *
     * <p>{@code AuthUser} 의 접근자는 {@code role()} 이고 {@code Member} 엔티티는 {@code getRole()} 이다 —
     * 이 <b>이름 차이가 actor 와 target 을 가른다.</b> 그래서 «행위자의 역할을 {@code ADMIN} 과 직접
     * 비교하는 것»만 정확히 집어낼 수 있다:
     *
     * <ul>
     *   <li>❌ {@code user.role() == Role.ADMIN} — 행위자 판정. <b>SUPER_ADMIN 이 떨어진다.</b></li>
     *   <li>⭕ {@code target.getRole() == Role.ADMIN} — 대상 판정
     *       ({@code MemberAdminCommandService}: «대상이 ADMIN 이면 SUPER 만 건드린다»). <b>의도대로다.</b></li>
     * </ul>
     *
     * <p>2026-08-10 §16-3 이 «일괄 치환 금지» 라고 못 박은 것이 바로 이 구분이라, 검사도 그 선을 따른다.
     * 새로 {@code user.role() == Role.ADMIN} 을 쓰면 여기서 파일·줄과 함께 빨개진다.
     */
    @Test
    @DisplayName("운영 코드에 행위자 역할의 == Role.ADMIN 비교가 남아 있지 않다 (일곱 번째 자리 방지)")
    void noActorSideRoleComparisonInMainSources() throws IOException {
        List<String> offenders = new ArrayList<>();
        Path main = Path.of("src/main/java");
        int scanned = 0;

        try (Stream<Path> files = Files.walk(main)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                scanned++;
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    // 문서에서 이 사고를 설명하는 주석은 검사 대상이 아니다 — 설명을 못 쓰게 하면 안 된다.
                    String code = line.trim();
                    if (code.startsWith("*") || code.startsWith("//") || code.startsWith("/*")) {
                        continue;
                    }
                    if (line.contains(".role() == Role.ADMIN") || line.contains(".role() != Role.ADMIN")) {
                        offenders.add(main.relativize(file) + ":" + (i + 1) + "  " + code);
                    }
                }
            }
        }

        // ⚠ WA §3-3 — 「0건」에는 이유가 둘이다(밟았는데 0인지, 안 밟아서 0인지).
        //    작업 디렉터리가 달라 한 파일도 못 읽으면 이 테스트는 **아무것도 안 보고** 초록이 된다.
        assertThat(scanned)
                .as("src/main/java 를 못 읽었다 — 이 테스트는 아무것도 검사하지 않았다(작업 디렉터리 확인)")
                .isGreaterThan(100);

        assertThat(offenders)
                .as("""
                        행위자 역할을 ADMIN 과 직접 비교하면 SUPER_ADMIN 이 떨어진다 \
                        (2026-08-10 §16-3 — 여섯 자리에서 실제 운영 계정이 막혀 있었다).
                        → AuthUser.isAdmin() / Role.isAdmin() 을 쓴다.
                        ⚠ 대상(target)의 역할을 보는 자리라면 Member.getRole() 이라 여기 걸리지 않는다.""")
                .isEmpty();
    }
}
