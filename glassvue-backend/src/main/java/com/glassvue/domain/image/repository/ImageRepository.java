package com.glassvue.domain.image.repository;

import com.glassvue.domain.image.entity.Image;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageRepository extends JpaRepository<Image, UUID> {

    List<Image> findByImageGroupIdOrderBySortOrderAsc(UUID imageGroupId);

    /** 여러 그룹의 이미지를 한 번에 (목록 N+1 회피). */
    List<Image> findByImageGroupIdInOrderBySortOrderAsc(Collection<UUID> imageGroupIds);

    /**
     * 어느 그룹에도 속하지 않은 채 방치된 이미지 — 업로드만 하고 저장하지 않은 것.
     * {@code createdAt} 기준으로 유예 시간을 두어, 작성 중인 폼의 이미지를 뺏지 않는다.
     */
    List<Image> findByImageGroupIsNullAndCreatedAtBefore(Instant createdAt);
}
