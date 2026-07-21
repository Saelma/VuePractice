package com.glassvue.domain.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.glassvue.domain.image.entity.Image;
import com.glassvue.domain.image.repository.ImageGroupRepository;
import com.glassvue.domain.image.repository.ImageRepository;
import com.glassvue.global.storage.FileStorageService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 파생본 백필 — V8 이전 업로드분에 medium·thumb를 채운다.
 * "재발 방지(업로드 시 생성)"와 "기존 잔재 정리(백필)"는 별개 작업이라는 §2-5의 사례.
 */
@ExtendWith(MockitoExtension.class)
class ImageServiceBackfillTest {

    @Mock FileStorageService fileStorageService;
    @Mock ImageRepository imageRepository;
    @Mock ImageGroupRepository imageGroupRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks ImageService service;

    private Image image(String url) {
        return Image.builder().url(url).originalName("o.png").contentType("image/png").size(1L).build();
    }

    @Test
    @DisplayName("대상이 없으면 아무 것도 하지 않는다")
    void noTargets() {
        when(imageRepository.findByMediumUrlIsNullOrThumbUrlIsNull()).thenReturn(List.of());

        ImageService.BackfillResult r = service.backfillDerivatives();

        assertThat(r.targets()).isZero();
        assertThat(r.updated()).isZero();
    }

    @Test
    @DisplayName("생성된 파생본 URL을 이미지에 반영한다")
    void fillsDerivatives() {
        Image img = image("/uploads/a.png");
        when(imageRepository.findByMediumUrlIsNullOrThumbUrlIsNull()).thenReturn(List.of(img));
        when(fileStorageService.generateDerivatives("/uploads/a.png"))
                .thenReturn(new FileStorageService.Derivatives("/uploads/a_m.webp", "/uploads/a_t.webp"));

        ImageService.BackfillResult r = service.backfillDerivatives();

        assertThat(img.getMediumUrl()).isEqualTo("/uploads/a_m.webp");
        assertThat(img.getThumbUrl()).isEqualTo("/uploads/a_t.webp");
        assertThat(r.targets()).isEqualTo(1);
        assertThat(r.updated()).isEqualTo(1);
        assertThat(r.skipped()).isZero();
    }

    @Test
    @DisplayName("원본이 없어 생성 못 하면 건너뛴다(값 안 건드림)")
    void skipsWhenNothingGenerated() {
        Image img = image("/uploads/gone.png");
        when(imageRepository.findByMediumUrlIsNullOrThumbUrlIsNull()).thenReturn(List.of(img));
        when(fileStorageService.generateDerivatives("/uploads/gone.png"))
                .thenReturn(new FileStorageService.Derivatives(null, null));

        ImageService.BackfillResult r = service.backfillDerivatives();

        assertThat(img.getMediumUrl()).isNull();
        assertThat(img.getThumbUrl()).isNull();
        assertThat(r.updated()).isZero();
        assertThat(r.skipped()).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 채워진 값은 덮어쓰지 않는다 — 여러 번 실행해도 안전")
    void doesNotOverwriteExisting() {
        Image img = image("/uploads/a.png");
        img.applyDerivatives("/uploads/OLD_m.webp", null); // medium은 이미 있음, thumb만 비어 있는 상태
        when(imageRepository.findByMediumUrlIsNullOrThumbUrlIsNull()).thenReturn(List.of(img));
        when(fileStorageService.generateDerivatives("/uploads/a.png"))
                .thenReturn(new FileStorageService.Derivatives("/uploads/NEW_m.webp", "/uploads/a_t.webp"));

        service.backfillDerivatives();

        assertThat(img.getMediumUrl()).isEqualTo("/uploads/OLD_m.webp"); // 유지
        assertThat(img.getThumbUrl()).isEqualTo("/uploads/a_t.webp");    // 빈 것만 채움
    }
}
