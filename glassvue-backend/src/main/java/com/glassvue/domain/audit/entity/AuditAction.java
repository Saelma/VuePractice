package com.glassvue.domain.audit.entity;

/**
 * 감사 대상 관리자 조작의 종류. 지금은 회원 조작(정지·해제·역할변경)만이지만, 앞으로 주문·상품 등
 * 다른 도메인의 관리자 조작도 같은 테이블에 남길 수 있게 값으로만 구분한다.
 *
 * <p>DB 에는 문자열로 저장(CHECK 제약, {@code V32}). 값을 추가할 때는 CHECK 제약도 함께 넓혀야 한다
 * (Oracle enum CHECK 트랩 — 메모리 참조).
 */
public enum AuditAction {
    /** 회원 정지. */
    MEMBER_SUSPEND,
    /** 회원 정지 해제. */
    MEMBER_UNSUSPEND,
    /** 회원 역할 변경(USER↔ADMIN). */
    MEMBER_ROLE_CHANGE,
    /**
     * 회원 강제 삭제(B-24, 2026-07-30). 되돌릴 수 없는 조작이라 <b>SUPER_ADMIN 전용</b>이다.
     *
     * <p>⚠ 이 값을 더할 때 {@code V35} 로 CHECK 제약을 함께 넓혔다 — {@code ddl-auto=update} 도 아니고
     * {@code validate} 라 제약은 절대 자동으로 안 따라온다(Oracle enum CHECK 트랩).
     */
    MEMBER_DELETE,
    /**
     * 관리자 대행 주문 취소(B-25, 2026-08-10). <b>회원이 아닌 것을 대상으로 하는 첫 값</b>이다 —
     * 정확히는 대상을 <b>주문자(회원)</b> 로 잡고 주문번호는 {@code detail} 에 넣는다.
     *
     * <p>⚠ 이 자리는 B-18(리뷰 숨김)에서 <b>감사를 붙이지 못했던</b> 그 자리다. 그때 막힌 이유는
     * *"{@code targetId} 가 대상 회원인데 리뷰는 회원이 아니다"* 였는데, <b>주문에는 주문자가 있어</b>
     * 같은 문제가 생기지 않는다. 즉 «감사가 회원 대상 설계라 못 쓴다» 는 도메인이 아니라
     * <b>대상에 회원이 있느냐</b>로 갈린다.
     *
     * <p>⚠ {@code V43} 으로 CHECK 제약을 함께 넓혔다 — {@code ddl-auto=validate} 라 절대 자동으로
     * 안 따라온다(Oracle enum CHECK 트랩). 넓히는 방향이라 구 jar 는 영향받지 않는다.
     */
    ORDER_CANCEL,
    /**
     * 리뷰 숨김 / 해제 · 문의 숨김 / 해제 (B-18 잔여, 2026-08-10) — <b>콘텐츠 조치</b>.
     *
     * <p>⚠ <b>B-18 은 2026-08-04 에 «감사를 못 붙인다» 며 접었다.</b> 이유는 *"{@code AdminActionEvent}
     * 가 회원 대상 설계라({@code targetId}=대상 회원) 리뷰에 안 맞는다"* 였는데, <b>그 전제가 틀렸다</b> —
     * 리뷰에는 {@code authorId}, 문의에는 {@code authorId} 가 있다. 같은 날 {@link #ORDER_CANCEL} 을
     * «대상=주문자» 로 붙이면서 드러났다: 갈리는 기준은 <b>도메인이 아니라 «대상에 회원이 있느냐»</b> 다.
     *
     * <p>대상은 <b>작성자</b>, 무엇을 숨겼는지는 {@code detail}(제목·상품명 등)에 넣는다.
     *
     * <p>🔴 <b>리뷰 숨김은 이미 배포된 기능이라 과거 조치는 감사에 없다</b> — 백필할 출처가 없다
     * ({@code review.hidden} 은 현재 상태만 갖고 «누가 언제» 를 모른다). 원장이 오늘부터 시작한다는
     * 뜻이고, 이건 «모르는 값» 이라 <b>지어내지 않는다</b>(V37 동의 시각과 같은 판단).
     *
     * <p>⚠ {@code V44} 로 CHECK 제약을 함께 넓혔다.
     */
    REVIEW_HIDE,
    REVIEW_UNHIDE,
    INQUIRY_HIDE,
    INQUIRY_UNHIDE,
    /**
     * 상품 삭제 대기(F-7 의 soft delete) / 되돌리기 (2026-08-14).
     *
     * <p>🔴 <b>«대상에 회원이 있느냐» 를 처음으로 못 넘는 값이다.</b> {@link #ORDER_CANCEL} 은 주문자를,
     * {@link #REVIEW_HIDE} 는 작성자를 대상으로 잡아 통과했는데 <b>상품에는 회원이 없다.</b>
     * 그래서 {@code targetId} 의 뜻을 넓혔다 — <b>«대상의 id, 그게 무엇인지는 이 action 이 말한다»</b>
     * (2026-08-14 사용자와 확정). {@code targetLogin} 은 {@code null} 이고 상품명은 {@code detail} 에 넣는다.
     *
     * <p>⚠ 대가를 하나 안다: 감사 화면의 검색은 <b>{@code targetLogin} 부분일치</b>뿐이라
     * (AdminAuditLogRepository) 상품 행은 <b>「조작 종류」 필터로만</b> 걸린다. 「대상 아이디」 열도 빈칸이다.
     * 이것이 «회원 아닌 대상» 이 처음 들어온 대가이고, 늘어나면 그때 {@code target_type} 을 세운다.
     *
     * <p>⚠ <b>영구 삭제(purge)는 남기지 않는다</b> — 부르는 것이 배치라 <b>행위자가 사람이 아니다</b>
     * ({@code actor_id} 는 NOT NULL 이고 지어낼 값이 아니다). 그 시점은 애플리케이션 로그에만 남는다.
     *
     * <p>🔴 <b>과거 삭제는 감사에 없다</b> — 백필할 출처가 없다({@code deleted_by_name} 은 «누가» 만 알고
     * «언제» 는 {@code deleted_at} 인데, <b>복구하면 둘 다 지워진다</b>). 원장은 오늘부터 시작한다
     * (V44 가 리뷰 숨김에서 한 판단과 같다 — 모르는 값은 지어내지 않는다).
     *
     * <p>⚠ {@code V50} 으로 CHECK 제약을 함께 넓혔다. 넓히는 방향이라 구 jar 는 영향받지 않는다.
     */
    PRODUCT_DELETE,
    PRODUCT_RESTORE
}
