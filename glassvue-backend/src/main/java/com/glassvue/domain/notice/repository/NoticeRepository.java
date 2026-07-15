package com.glassvue.domain.notice.repository;

import java.util.UUID;
import com.glassvue.domain.notice.entity.Notice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, UUID>, NoticeRepositoryCustom {
}
