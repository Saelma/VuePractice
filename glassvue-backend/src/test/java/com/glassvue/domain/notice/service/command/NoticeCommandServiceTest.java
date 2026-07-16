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

    private final AuthUser user = new AuthUser(UUID.randomUUID(), Role.USER, "kim");
    private final AuthUser admin = new AuthUser(UUID.randomUUID(), Role.ADMIN, "admin");

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
    @DisplayName("수정: 남의 글 → NOTICE_NOT_OWNER")
    void update_notOwner() {
        when(noticeRepository.findById(any())).thenReturn(Optional.of(noticeBy(UUID.randomUUID())));
        assertErrorCode(() -> service.update(UUID.randomUUID(), new NoticeUpdateRequest("t", "c", false), user),
                ErrorCode.NOTICE_NOT_OWNER);
    }

    @Test
    @DisplayName("수정: 본인 글 → 반영")
    void update_owner() {
        Notice mine = noticeBy(user.id());
        when(noticeRepository.findById(any())).thenReturn(Optional.of(mine));
        service.update(UUID.randomUUID(), new NoticeUpdateRequest("새제목", "새본문", true), user);
        assertThat(mine.getTitle()).isEqualTo("새제목");
        assertThat(mine.isPinned()).isTrue();
    }

    @Test
    @DisplayName("삭제: 관리자는 남의 글도 삭제 가능")
    void delete_admin() {
        Notice other = noticeBy(UUID.randomUUID());
        when(noticeRepository.findById(any())).thenReturn(Optional.of(other));
        service.delete(UUID.randomUUID(), admin);
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
