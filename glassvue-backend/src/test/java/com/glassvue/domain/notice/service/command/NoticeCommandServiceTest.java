package com.glassvue.domain.notice.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.notice.dto.NoticeCreateRequest;
import com.glassvue.domain.notice.dto.NoticeUpdateRequest;
import com.glassvue.domain.notice.entity.Notice;
import com.glassvue.domain.notice.repository.NoticeRepository;
import com.glassvue.domain.notice.viewcount.NoticeViewCountStore;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.security.AuthUser;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NoticeCommandServiceTest {

    @Mock NoticeRepository noticeRepository;
    @Mock NoticeViewCountStore viewCountStore;
    @InjectMocks NoticeCommandService service;

    private final AuthUser user = new AuthUser(UUID.randomUUID(), Role.ADMIN, "admin");

    private Notice noticeBy(UUID authorId) {
        return Notice.builder().title("t").content("c").author("nick").authorId(authorId).pinned(false).build();
    }
    private static void assertErrorCode(Runnable r, ErrorCode expected) {
        assertThatThrownBy(r::run).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(expected);
    }

    @Test
    @DisplayName("작성: 저장 후 id 반환")
    void create() {
        when(noticeRepository.save(any(Notice.class))).thenAnswer(inv -> inv.getArgument(0));
        UUID id = service.create(new NoticeCreateRequest("제목", "본문", false), user.id(), user.nickname());
        assertThat(id).isNotNull();
        verify(noticeRepository).save(any(Notice.class));
    }

    @Test
    @DisplayName("수정: 없는 글 → NOTICE_NOT_FOUND")
    void update_notFound() {
        when(noticeRepository.findById(any())).thenReturn(Optional.empty());
        assertErrorCode(() -> service.update(UUID.randomUUID(), new NoticeUpdateRequest("t", "c", false)),
                ErrorCode.NOTICE_NOT_FOUND);
    }

    @Test
    @DisplayName("수정: 남의 글도 고친다 — 🔴 공지는 관리자 콘텐츠라 소유권 개념이 없다")
    void update_anyNotice() {
        // ⚠ 2026-08-20(BACKLOG E-4) 전에는 «본인 글만» 이었고, 남의 글이면 NOTICE_NOT_OWNER 였다.
        //    공지가 관리자 전용이 되면서 그 갈래는 **도달할 수 없다** — 여기 오는 요청은 이미 관리자다.
        //    🔴 권한은 SecurityConfig 한 곳이 본다(서비스에 같은 규칙을 또 두면 죽은 코드가 된다).
        //    «관리자 아닌 요청이 막히는가» 는 AuthFlowIntegrationTest 가 403 으로 본다.
        Notice other = noticeBy(UUID.randomUUID());
        when(noticeRepository.findById(any())).thenReturn(Optional.of(other));
        service.update(UUID.randomUUID(), new NoticeUpdateRequest("새제목", "새본문", true));
        assertThat(other.getTitle()).isEqualTo("새제목");
        assertThat(other.isPinned()).isTrue();
    }

    @Test
    @DisplayName("삭제: 남의 글도 삭제된다")
    void delete_anyNotice() {
        Notice other = noticeBy(UUID.randomUUID());
        when(noticeRepository.findById(any())).thenReturn(Optional.of(other));
        service.delete(UUID.randomUUID());
        verify(noticeRepository).delete(other);
    }

    @Test
    @DisplayName("조회수 증가는 Redis 스토어에 위임(존재 확인 안 함)")
    void increaseView() {
        UUID id = UUID.randomUUID();
        service.increaseView(id);
        verify(viewCountStore).increment(id);
    }
}
