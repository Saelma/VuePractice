package com.glassvue.domain.notice.repository;

import java.util.UUID;
import com.glassvue.domain.notice.entity.Notice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoticeRepository extends JpaRepository<Notice, UUID>, NoticeRepositoryCustom {

    /** 조회수 누적분을 DB에 원자적으로 더한다 (Flusher 전용). */
    @Modifying
    @Query("update Notice n set n.viewCount = n.viewCount + :delta where n.id = :id")
    void addViewCount(@Param("id") UUID id, @Param("delta") long delta);
}
