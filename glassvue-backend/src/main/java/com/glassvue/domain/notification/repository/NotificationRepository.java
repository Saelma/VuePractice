package com.glassvue.domain.notification.repository;

import com.glassvue.domain.notification.entity.Notification;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** 내 알림 최신순(페이징). 알림함의 유일한 목록 조회 경로다. */
    Page<Notification> findByMemberIdOrderByCreatedAtDesc(UUID memberId, Pageable pageable);

    /** 벨 뱃지에 쓰는 안읽음 수. */
    long countByMemberIdAndReadFalse(UUID memberId);

    /** 읽음 처리는 본인 알림만 — id 로만 찾으면 남의 알림을 읽음 처리할 수 있다. */
    Optional<Notification> findByIdAndMemberId(UUID id, UUID memberId);

    /** 모두 읽음 — 안읽은 것만 벌크 UPDATE(전체 로딩·더티체킹 불필요). 반영된 행 수 반환. */
    @Modifying
    @Query("update Notification n set n.read = true where n.memberId = :memberId and n.read = false")
    int markAllRead(@Param("memberId") UUID memberId);

    /** 회원 삭제 정리용(F-1). */
    long deleteByMemberId(UUID memberId);

    /**
     * 그 상품을 가리키는 알림을 지운다 (F-7 purge, 2026-09-10 · BACKLOG M-4).
     *
     * <p>⚠ <b>접두사로 지운다</b> — 링크가 {@code /products/<id>} 뿐 아니라
     * {@code /products/<id>#inquiries}(B-15 문의 답변 알림) 로도 온다. 뒤를 안 맞추면 그것만 남는다.
     *
     * <p>🔴 <b>{@code link} 는 문자열이고 FK 가 없다.</b> 그래서 상품이 사라져도 DB 가 아무것도 안 한다 —
     * 2026-09-10 에 <b>없는 상품을 가리키는 알림 55건</b>이 그렇게 쌓여 있었다.
     * ⚠ id 를 따로 저장하지 않는 이유는 알림이 <b>여러 도메인</b>을 가리키기 때문이다(주문·상품·문의).
     *
     * <p>🔴 <b>{@code flushAutomatically}·{@code clearAutomatically} 가 둘 다 필요하다.</b>
     * 벌크 JPQL 은 <b>영속성 컨텍스트를 지나쳐</b> DB 에 바로 간다 —
     * ①앞서 저장한 알림이 아직 flush 안 됐으면 <b>지울 대상에 안 들어가고</b>
     * ②지운 뒤에도 1차 캐시에 남아 {@code findById} 가 <b>사라진 행을 돌려준다.</b>
     * ⚠ 감사 CHECK 제약에서 겪은 것과 같은 계열이다 — *"{@code @Transactional} 롤백만 하면
     * INSERT 가 DB 에 안 닿아 제약이 한 번도 실행되지 않는다"*.
     * 🔴 <b>2026-09-10 에 이것 없이 썼다가 테스트 5개 중 4개가 «안 지워졌다» 로 빨개졌다</b> —
     * 운영에서는 조용히 «지웠다고 믿는» 상태가 됐을 자리다.
     *
     * @param productId 대시를 포함한 문자열 형태 — 링크에 그 모양으로 박혀 있다
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Notification n where n.link like concat('/products/', :productId, '%')")
    int deleteByProductLink(@Param("productId") String productId);
}
