package com.glassvue.domain.image.repository;

import com.glassvue.domain.image.entity.Image;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageRepository extends JpaRepository<Image, UUID> {

    List<Image> findByImageGroupIdOrderBySortOrderAsc(UUID imageGroupId);

    /** 여러 그룹의 이미지를 한 번에 (목록 N+1 회피). */
    List<Image> findByImageGroupIdInOrderBySortOrderAsc(Collection<UUID> imageGroupIds);
}
