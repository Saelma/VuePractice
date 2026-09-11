package com.glassvue.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.glassvue.domain.member.entity.Role;
import com.glassvue.global.security.AuthUser;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 작성자 표시 이름 마스킹 (R-7). 형식은 «앞 1글자 + 고정 {@code **}» (2026-09-11 사용자 결정).
 *
 * <p>⚠ 기대값은 <b>다른 형식과 갈리게</b> 골랐다 — 5글자 이름이라 «앞 2글자»(김기**)·«길이만큼 별»(김****)
 * 과 전부 다르게 나온다.
 */
class AuthorNamesTest {

    private static final AuthUser OTHER = new AuthUser(UUID.randomUUID(), Role.USER, "other");
    private static final AuthUser ADMIN = new AuthUser(UUID.randomUUID(), Role.ADMIN, "admin");

    @Test
    @DisplayName("앞 1글자 + 고정 ** — 길이가 새지 않는다")
    void mask_firstCharPlusFixedStars() {
        assertThat(AuthorNames.mask("김기현팀3")).isEqualTo("김**");
        assertThat(AuthorNames.mask("홍길동")).isEqualTo("홍**");
        assertThat(AuthorNames.mask("한")).isEqualTo("한**");
    }

    @Test
    @DisplayName("첫 글자가 이모지여도 반쪽으로 쪼개지 않는다(코드 포인트로 자른다)")
    void mask_surrogatePairKeptWhole() {
        assertThat(AuthorNames.mask("😀하하")).isEqualTo("😀**");
    }

    @Test
    @DisplayName("빈 값은 그대로 — 가릴 것이 없다")
    void mask_emptyUntouched() {
        assertThat(AuthorNames.mask("")).isEmpty();
        assertThat(AuthorNames.mask(null)).isNull();
    }

    @Test
    @DisplayName("비로그인·타인에게는 가리고, 본인·관리자에게는 원문")
    void forViewer_ownerAndAdminSeeFullName() {
        assertThat(AuthorNames.forViewer("김기현팀3", false, null)).isEqualTo("김**");
        assertThat(AuthorNames.forViewer("김기현팀3", false, OTHER)).isEqualTo("김**");
        assertThat(AuthorNames.forViewer("김기현팀3", true, OTHER)).isEqualTo("김기현팀3");
        assertThat(AuthorNames.forViewer("김기현팀3", false, ADMIN)).isEqualTo("김기현팀3");
    }
}
