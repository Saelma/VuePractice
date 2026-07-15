package com.glassvue.domain.image.repository;

import com.glassvue.domain.image.entity.ImageGroup;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageGroupRepository extends JpaRepository<ImageGroup, UUID> {
}
