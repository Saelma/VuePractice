package com.glassvue.domain.notice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassvue.domain.notice.dto.NoticeSearchCondition;
import com.glassvue.domain.notice.entity.Notice;
import com.glassvue.global.config.JpaAuditingConfig;
import com.glassvue.global.config.QuerydslConfig;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * NoticeRepository QueryDSL 통합 테스트 — 실제 Oracle 대상.
 * ConditionBuilder(@Cond 동적검색)·QueryDslSupport(페이징)·SortSupport(정렬 화이트리스트)를 함께 검증.
 * DB_HOST 있을 때만 실행(= .env 소싱). @DataJpaTest는 각 테스트를 트랜잭션 롤백 → 공유 DB 무오염.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // 임베디드 대체 금지 → 실제 Oracle
@Import({QuerydslConfig.class, JpaAuditingConfig.class}) // 커스텀 리포지토리의 JPAQueryFactory + 감사
class NoticeRepositoryIntegrationTest {

    @Autowired NoticeRepository noticeRepository;

    private static final String MARK = "ZZINTEG"; // 이 테스트가 넣은 데이터만 걸러내는 표식

    @BeforeEach
    void setUp() {
        noticeRepository.save(Notice.builder().title(MARK + "-알파").content("c").author("홍길동").pinned(false).build());
        noticeRepository.save(Notice.builder().title(MARK + "-베타").content("c").author("김철수").pinned(true).build());
        noticeRepository.save(Notice.builder().title("무관한 공지").content("c").author("이몽룡").pinned(false).build());
    }

    private Pageable page(Sort sort) {
        return PageRequest.of(0, 10, sort);
    }

    @Test
    @DisplayName("제목 CONTAINS 동적검색 — 표식 붙은 2건만")
    void searchByTitle() {
        var cond = new NoticeSearchCondition(MARK, null, null, null);
        var result = noticeRepository.search(cond, page(Sort.unsorted()));
        assertThat(result.getContent()).hasSize(2)
                .allSatisfy(n -> assertThat(n.getTitle()).contains(MARK));
    }

    @Test
    @DisplayName("작성자 CONTAINS 동적검색")
    void searchByAuthor() {
        var cond = new NoticeSearchCondition(MARK, "김철수", null, null);
        var result = noticeRepository.search(cond, page(Sort.unsorted()));
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getAuthor()).isEqualTo("김철수");
    }

    @Test
    @DisplayName("허용된 정렬 필드(title)는 정상")
    void sortAllowed() {
        var cond = new NoticeSearchCondition(MARK, null, null, null);
        var result = noticeRepository.search(cond, page(Sort.by("title").ascending()));
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("화이트리스트 밖 정렬 필드(content) → INVALID_INPUT")
    void sortNotAllowed() {
        var cond = new NoticeSearchCondition(MARK, null, null, null);
        assertThatThrownBy(() -> noticeRepository.search(cond, page(Sort.by("content"))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }
}
