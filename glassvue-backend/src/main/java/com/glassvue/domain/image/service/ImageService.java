package com.glassvue.domain.image.service;

import com.glassvue.domain.image.dto.ImageResponse;
import com.glassvue.domain.image.entity.Image;
import com.glassvue.domain.image.entity.ImageGroup;
import com.glassvue.domain.image.repository.ImageGroupRepository;
import com.glassvue.domain.image.repository.ImageRepository;
import com.glassvue.domain.image.event.ImageFilesReleasedEvent;
import com.glassvue.global.storage.FileStorageService;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 이미지 도메인 공개 서비스. 업로드(파일+메타), 그룹 생성·연결, 그룹별 조회, 정리.
 * 다른 도메인(catalog 등)은 image_group_id만 들고 이 서비스로 이미지를 다룬다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ImageService {

    private final FileStorageService fileStorageService;
    private final ImageRepository imageRepository;
    private final ImageGroupRepository imageGroupRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** 파일 저장 + image row 생성(그룹 미지정). */
    public ImageResponse upload(MultipartFile file) {
        FileStorageService.Stored stored = fileStorageService.store(file);
        Image image = imageRepository.save(Image.builder()
                .url(stored.url())
                .mediumUrl(stored.mediumUrl())
                .thumbUrl(stored.thumbUrl())
                .originalName(stored.originalName())
                .contentType(stored.contentType())
                .size(stored.size())
                .build());
        return ImageResponse.from(image);
    }

    /** 파생본 백필 결과. */
    public record BackfillResult(int targets, int updated, int skipped) {
    }

    /**
     * 파생본이 없는 기존 이미지에 medium·thumb를 생성해 채운다(V8 이전 업로드분).
     *
     * <p>"앞으로 안 생기게" 고치는 것과 "이미 쌓인 걸 치우는" 건 별개 작업이라 따로 돌린다
     * (WORKING-AGREEMENTS §2-5). 업로드와 같은 생성 코드를 타므로 결과가 어긋나지 않고,
     * 이미 채워진 값은 덮어쓰지 않아 <b>여러 번 실행해도 안전</b>하다.
     * 원본 파일이 없거나 디코딩이 안 되는 건은 건너뛴다(skipped).
     */
    public BackfillResult backfillDerivatives() {
        List<Image> targets = imageRepository.findByMediumUrlIsNullOrThumbUrlIsNull();
        int updated = 0;
        for (Image image : targets) {
            FileStorageService.Derivatives d = fileStorageService.generateDerivatives(image.getUrl());
            if (d.none()) {
                continue;
            }
            image.applyDerivatives(d.mediumUrl(), d.thumbUrl());
            updated++;
        }
        log.info("이미지 파생본 백필: 대상={} 갱신={} 건너뜀={}", targets.size(), updated, targets.size() - updated);
        return new BackfillResult(targets.size(), updated, targets.size() - updated);
    }

    /** imageIds로 새 그룹 생성·연결(순서 유지). 비어 있으면 null 반환. */
    public UUID createGroup(List<UUID> imageIds) {
        if (imageIds == null || imageIds.isEmpty()) {
            return null;
        }
        ImageGroup group = imageGroupRepository.save(ImageGroup.create());
        Map<UUID, Image> byId = imageRepository.findAllById(imageIds).stream()
                .collect(Collectors.toMap(Image::getId, i -> i));
        int order = 0;
        for (UUID id : imageIds) {
            Image image = byId.get(id);
            if (image != null) {
                image.assignToGroup(group, order++);
            }
        }
        return group.getId();
    }

    @Transactional(readOnly = true)
    public List<ImageResponse> findByGroup(UUID groupId) {
        if (groupId == null) {
            return List.of();
        }
        return imageRepository.findByImageGroupIdOrderBySortOrderAsc(groupId).stream()
                .map(ImageResponse::from)
                .toList();
    }

    /**
     * 더 이상 쓰이지 않는 그룹과 거기 남은 이미지를 지운다 — **소유 도메인이 그룹을 교체할 때** 호출한다.
     *
     * <p><b>반드시 {@link #createGroup} 뒤에 부를 것.</b> createGroup은 새 목록에 있는 이미지를
     * 새 그룹으로 **재할당**하므로, 그 뒤에 남아 있는 건 사용자가 **제거한 이미지**뿐이다.
     * 순서를 바꾸면 유지하려던 이미지까지 지워진다.
     *
     * <p>이 정리를 스위퍼로 못 하는 이유: 버려진 그룹인지 알려면 catalog·review가 어떤 그룹을
     * 참조하는지 봐야 하는데, 그건 image → 타 도메인 역방향 의존이라 경계를 깬다.
     * 교체하는 쪽은 옛 그룹 id를 이미 알고 있으므로 그쪽이 알리는 게 맞다.
     */
    public void deleteGroup(UUID groupId) {
        if (groupId == null) {
            return;
        }
        List<Image> images = imageRepository.findByImageGroupIdOrderBySortOrderAsc(groupId);
        deleteImagesAndPublish(images);
        imageGroupRepository.deleteById(groupId);
    }

    /**
     * 어느 그룹에도 속하지 않은 채 방치된 이미지를 정리한다(업로드만 하고 저장하지 않은 것).
     *
     * <p>{@code uploadedBefore} 이전 것만 지운다 — 지금 막 올려두고 아직 저장 버튼을 누르지 않은
     * 이미지를 뺏지 않기 위한 유예 시간이다.
     *
     * @return 삭제한 이미지 수
     */
    public int sweepOrphans(Instant uploadedBefore) {
        List<Image> orphans = imageRepository.findByImageGroupIsNullAndCreatedAtBefore(uploadedBefore);
        deleteImagesAndPublish(orphans);
        return orphans.size();
    }

    /** row를 지우고, 실제 파일 삭제는 커밋 후로 미룬다(롤백 시 깨진 이미지 방지). */
    private void deleteImagesAndPublish(List<Image> images) {
        if (images.isEmpty()) {
            return;
        }
        // 원본 + 파생본(medium·thumb) 파일을 모두 지워야 한다(파생본이 남으면 고아 파일).
        List<String> urls = images.stream()
                .flatMap(i -> Stream.of(i.getUrl(), i.getMediumUrl(), i.getThumbUrl()))
                .filter(Objects::nonNull)
                .toList();
        imageRepository.deleteAll(images);
        eventPublisher.publishEvent(new ImageFilesReleasedEvent(urls));
    }

    /** groupId → 이미지들 (목록에서 N+1 회피용 일괄 조회). */
    @Transactional(readOnly = true)
    public Map<UUID, List<ImageResponse>> findByGroups(Collection<UUID> groupIds) {
        if (groupIds.isEmpty()) {
            return Map.of();
        }
        return imageRepository.findByImageGroupIdInOrderBySortOrderAsc(groupIds).stream()
                .collect(Collectors.groupingBy(
                        i -> i.getImageGroup().getId(),
                        Collectors.mapping(ImageResponse::from, Collectors.toList())));
    }
}
