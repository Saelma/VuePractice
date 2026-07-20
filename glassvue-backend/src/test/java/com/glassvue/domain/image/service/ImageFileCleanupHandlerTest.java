package com.glassvue.domain.image.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.glassvue.domain.image.event.ImageFilesReleasedEvent;
import com.glassvue.global.storage.FileStorageService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImageFileCleanupHandlerTest {

    @Mock FileStorageService fileStorageService;
    @InjectMocks ImageFileCleanupHandler handler;

    @Test
    @DisplayName("이벤트의 url을 모두 삭제 요청한다")
    void deletesEveryUrl() {
        when(fileStorageService.delete("/uploads/a.png")).thenReturn(true);
        when(fileStorageService.delete("/uploads/b.png")).thenReturn(false); // 이미 없어도 계속 진행

        handler.handle(new ImageFilesReleasedEvent(List.of("/uploads/a.png", "/uploads/b.png")));

        verify(fileStorageService).delete("/uploads/a.png");
        verify(fileStorageService).delete("/uploads/b.png");
    }

    @Test
    @DisplayName("빈 목록이면 파일 서비스를 건드리지 않는다")
    void emptyIsNoop() {
        handler.handle(new ImageFilesReleasedEvent(List.of()));
        verifyNoInteractions(fileStorageService);
    }
}
