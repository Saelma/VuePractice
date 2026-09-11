package com.glassvue.global.common;

import com.glassvue.global.security.AuthUser;

/**
 * <b>공개 목록의 작성자 표시 이름을 가리는 한 곳</b> (BACKLOG R-7).
 *
 * <p>🔴 <b>화면에서 자르지 않는다.</b> 응답에 원문이 실리는 순간 이미 나간 것이고, 문의·리뷰 두 화면이
 * 따로 자르면 규칙이 갈린다. {@code InquiryResponse.from}·{@code ReviewResponse.from} 이 여기를 부른다.
 *
 * <p>⚠ <b>별 개수는 고정</b>이다 — 이름 길이만큼 찍으면 길이가 새어 나간다.
 * 첫 글자는 <b>코드 포인트</b>로 자른다(이모지 첫 글자를 반쪽으로 쪼개지 않게).
 */
public final class AuthorNames {

    private static final String MASK = "**";

    private AuthorNames() {
    }

    /**
     * 본인·관리자에게는 원문, 나머지(비로그인 포함)에게는 가린 이름.
     *
     * <p>⚠ {@code mine} 을 따로 받는 이유: 판정은 응답이 이미 했고(작성자 id 비교),
     * 여기서 다시 하면 «내 글인가» 가 두 곳에서 계산된다.
     */
    public static String forViewer(String author, boolean mine, AuthUser viewer) {
        if (mine || (viewer != null && viewer.isAdmin())) {
            return author;
        }
        return mask(author);
    }

    /** {@code 김기현팀3 → 김**}. 빈 값은 그대로 둔다(가릴 것이 없다). */
    public static String mask(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return name.substring(0, name.offsetByCodePoints(0, 1)) + MASK;
    }
}
