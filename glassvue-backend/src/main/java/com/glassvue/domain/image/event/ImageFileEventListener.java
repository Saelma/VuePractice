package com.glassvue.domain.image.event;

import com.glassvue.domain.image.service.ImageFileCleanupHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 이미지 파일 삭제 리스너(어댑터). 기존 3층 규약 그대로 — 수신·위임만, 로직 없음.
 *
 * <p>{@code AFTER_COMMIT}: 롤백 시 파일이 사라져 깨진 이미지가 되는 걸 막는다.
 * {@code @Async}: 파일 I/O를 요청 스레드에서 분리(이벤트 풀 event-*).
 */
@Component
@RequiredArgsConstructor
public class ImageFileEventListener {

    private final ImageFileCleanupHandler imageFileCleanupHandler;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onImageFilesReleased(ImageFilesReleasedEvent event) {
        imageFileCleanupHandler.handle(event);
    }
}
