package com.glassvue.domain.review.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.review.entity.Review;
import com.glassvue.global.security.AuthUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 리뷰 응답의 작성자 표시 이름 (R-7). 규칙 자체는 {@code AuthorNamesTest} 가 보고,
 * 여기는 <b>{@code from} 이 그 규칙을 실제로 거치는가</b>를 본다.
 */
class ReviewResponseTest {

    private final UUID ownerId = UUID.randomUUID();

    private Review review() {
        return Review.builder().productId(UUID.randomUUID()).authorId(ownerId)
                .author("김기현팀3").rating(5).content("좋아요").imageGroupId(null).build();
    }

    @Test
    @DisplayName("비로그인·타인에게는 작성자 이름을 가린다")
    void author_maskedForAnonymousAndOthers() {
        assertThat(ReviewResponse.from(review(), null, List.of()).author()).isEqualTo("김**");
        assertThat(ReviewResponse.from(review(),
                new AuthUser(UUID.randomUUID(), Role.USER, "other"), List.of()).author()).isEqualTo("김**");
    }

    @Test
    @DisplayName("본인·관리자에게는 원문 이름")
    void author_fullForOwnerAndAdmin() {
        assertThat(ReviewResponse.from(review(),
                new AuthUser(ownerId, Role.USER, "me"), List.of()).author()).isEqualTo("김기현팀3");
        assertThat(ReviewResponse.from(review(),
                new AuthUser(UUID.randomUUID(), Role.ADMIN, "admin"), List.of()).author()).isEqualTo("김기현팀3");
    }
}
