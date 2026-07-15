package kr.co.ecstel.esp.domain.notice.repository;

import kr.co.ecstel.esp.domain.notice.dto.NoticeSearchCondition;
import kr.co.ecstel.esp.domain.notice.entity.Notice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NoticeRepositoryCustom {
    Page<Notice> search(NoticeSearchCondition condition, Pageable pageable);
}
