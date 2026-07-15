package com.glassvue.domain.image.service;

import com.glassvue.domain.image.dto.ImageResponse;
import com.glassvue.domain.image.entity.Image;
import com.glassvue.domain.image.entity.ImageGroup;
import com.glassvue.domain.image.repository.ImageGroupRepository;
import com.glassvue.domain.image.repository.ImageRepository;
import com.glassvue.global.storage.FileStorageService;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 이미지 도메인 공개 서비스. 업로드(파일+메타), 그룹 생성·연결, 그룹별 조회.
 * 다른 도메인(catalog 등)은 image_group_id만 들고 이 서비스로 이미지를 다룬다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ImageService {

    private final FileStorageService fileStorageService;
    private final ImageRepository imageRepository;
    private final ImageGroupRepository imageGroupRepository;

    /** 파일 저장 + image row 생성(그룹 미지정). */
    public ImageResponse upload(MultipartFile file) {
        FileStorageService.Stored stored = fileStorageService.store(file);
        Image image = imageRepository.save(Image.builder()
                .url(stored.url())
                .originalName(stored.originalName())
                .contentType(stored.contentType())
                .size(stored.size())
                .build());
        return ImageResponse.from(image);
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
