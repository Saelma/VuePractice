package com.glassvue.domain.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassvue.domain.image.entity.Image;
import com.glassvue.domain.image.event.ImageFilesReleasedEvent;
import com.glassvue.domain.image.repository.ImageGroupRepository;
import com.glassvue.domain.image.repository.ImageRepository;
import com.glassvue.global.storage.FileStorageService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** 고아 이미지 정리 — 그룹 삭제(교체·삭제 경로)와 미사용 업로드 스위핑. */
@ExtendWith(MockitoExtension.class)
class ImageServiceCleanupTest {

    @Mock FileStorageService fileStorageService;
    @Mock ImageRepository imageRepository;
    @Mock ImageGroupRepository imageGroupRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks ImageService service;

    private final UUID groupId = UUID.randomUUID();

    private Image image(String url) {
        return Image.builder().url(url).originalName("o.png").contentType("image/png").size(1L).build();
    }

    private ImageFilesReleasedEvent capturedEvent() {
        ArgumentCaptor<ImageFilesReleasedEvent> captor = ArgumentCaptor.forClass(ImageFilesReleasedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("그룹 삭제: 남은 이미지 row를 지우고 파일 삭제 이벤트를 발행한다")
    void deleteGroup_removesImagesAndPublishes() {
        List<Image> images = List.of(image("/uploads/a.png"), image("/uploads/b.png"));
        when(imageRepository.findByImageGroupIdOrderBySortOrderAsc(groupId)).thenReturn(images);

        service.deleteGroup(groupId);

        verify(imageRepository).deleteAll(images);
        verify(imageGroupRepository).deleteById(groupId);
        assertThat(capturedEvent().urls()).containsExactly("/uploads/a.png", "/uploads/b.png");
    }

    @Test
    @DisplayName("그룹 삭제: 남은 이미지가 없으면 이벤트 없이 그룹만 지운다")
    void deleteGroup_emptyGroup() {
        when(imageRepository.findByImageGroupIdOrderBySortOrderAsc(groupId)).thenReturn(List.of());

        service.deleteGroup(groupId);

        verify(imageGroupRepository).deleteById(groupId);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("그룹 삭제: groupId가 null이면 아무것도 하지 않는다(이미지 없는 상품·리뷰)")
    void deleteGroup_nullIsNoop() {
        service.deleteGroup(null);

        verify(imageRepository, never()).deleteAll(anyList());
        verify(imageGroupRepository, never()).deleteById(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("스위핑: 유예 시간이 지난 미사용 이미지를 지우고 개수를 반환한다")
    void sweepOrphans_deletesOld() {
        Instant threshold = Instant.parse("2026-07-20T00:00:00Z");
        List<Image> orphans = List.of(image("/uploads/orphan.png"));
        when(imageRepository.findByImageGroupIsNullAndCreatedAtBefore(threshold)).thenReturn(orphans);

        assertThat(service.sweepOrphans(threshold)).isEqualTo(1);

        verify(imageRepository).deleteAll(orphans);
        assertThat(capturedEvent().urls()).containsExactly("/uploads/orphan.png");
    }

    @Test
    @DisplayName("스위핑: 대상이 없으면 이벤트를 발행하지 않는다")
    void sweepOrphans_none() {
        Instant threshold = Instant.parse("2026-07-20T00:00:00Z");
        when(imageRepository.findByImageGroupIsNullAndCreatedAtBefore(threshold)).thenReturn(List.of());

        assertThat(service.sweepOrphans(threshold)).isZero();

        verify(eventPublisher, never()).publishEvent(any());
    }
}
