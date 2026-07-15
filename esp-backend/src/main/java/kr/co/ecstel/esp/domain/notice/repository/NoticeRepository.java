package kr.co.ecstel.esp.domain.notice.repository;

import java.util.UUID;
import kr.co.ecstel.esp.domain.notice.entity.Notice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, UUID>, NoticeRepositoryCustom {
}
