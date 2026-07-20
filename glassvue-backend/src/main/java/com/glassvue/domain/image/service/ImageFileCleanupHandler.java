package com.glassvue.domain.image.service;

import com.glassvue.domain.image.event.ImageFilesReleasedEvent;
import com.glassvue.global.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 실제 파일 삭제를 수행하는 주체(Handler). 리스너는 위임만 하고 로직은 여기 있다
 * — MSA 전환 시 리스너 자리에 메시지 컨슈머만 갈아끼우고 이 클래스는 그대로 재사용한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageFileCleanupHandler {

    private final FileStorageService fileStorageService;

    public void handle(ImageFilesReleasedEvent event) {
        if (event.urls().isEmpty()) {
            return;
        }
        int deleted = 0;
        for (String url : event.urls()) {
            if (fileStorageService.delete(url)) {
                deleted++;
            }
        }
        log.info("[이미지] 파일 정리 — 요청 {}건, 삭제 {}건", event.urls().size(), deleted);
    }
}
