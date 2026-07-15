package com.glassvue.domain.notice.repository;

import com.glassvue.domain.notice.dto.NoticeSearchCondition;
import com.glassvue.domain.notice.entity.Notice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NoticeRepositoryCustom {
    Page<Notice> search(NoticeSearchCondition condition, Pageable pageable);
}
