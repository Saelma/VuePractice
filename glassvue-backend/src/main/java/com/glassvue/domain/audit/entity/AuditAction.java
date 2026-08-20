package com.glassvue.domain.audit.entity;

/**
 * 감사 대상 관리자 조작의 종류. 회원·주문·리뷰·문의·상품·쿠폰·할인이 <b>한 테이블</b>에 남고
 * 값으로만 구분한다.
 *
 * <p>🔴 <b>각 값은 자기 대상이 무엇인지도 안다</b>({@link #targetType()}, 2026-08-20 V53).
 * 그전까지 그 사실은 <b>주석에만</b> 있었다 — {@code AdminActionEvent} 가 *"무엇인지는 action 이
 * 말한다"* 라 적어 두고 값은 아무 데도 안 남겼다. 이벤트 파라미터로 받지 않는 이유는
 * «action 은 {@code PRODUCT_UPDATE} 인데 targetType 은 {@code MEMBER}» 인 행을 <b>만들 수 없게</b>
 * 하기 위해서다.
 *
 * <p>⚠ <b>대상은 «id 를 물어볼 값어치가 있는 쪽» 으로 잡는다.</b> 주문 조작의 대상이 주문이 아니라
 * <b>주문자</b>이고(V43), 할인 조작의 대상이 할인이 아니라 <b>상품</b>인(V53) 이유가 같다.
 *
 * <p>DB 에는 문자열로 저장(CHECK 제약, {@code V32}). 값을 추가할 때는 CHECK 제약도 함께 넓혀야 한다
 * (Oracle enum CHECK 트랩 — 메모리 참조). ⚠ {@code action} 열은 <b>20자</b>다 —
 * {@code ORDER_RETURN_APPROVE} 가 정확히 20자라 여유가 없다.
 */
public enum AuditAction {
    /** 회원 정지. */
    MEMBER_SUSPEND(AuditTargetType.MEMBER),
    /** 회원 정지 해제. */
    MEMBER_UNSUSPEND(AuditTargetType.MEMBER),
    /** 회원 역할 변경(USER↔ADMIN). */
    MEMBER_ROLE_CHANGE(AuditTargetType.MEMBER),
    /**
     * 회원 강제 삭제(B-24, 2026-07-30). 되돌릴 수 없는 조작이라 <b>SUPER_ADMIN 전용</b>이다.
     *
     * <p>⚠ 이 값을 더할 때 {@code V35} 로 CHECK 제약을 함께 넓혔다 — {@code ddl-auto=update} 도 아니고
     * {@code validate} 라 제약은 절대 자동으로 안 따라온다(Oracle enum CHECK 트랩).
     */
    MEMBER_DELETE(AuditTargetType.MEMBER),
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
    ORDER_CANCEL(AuditTargetType.MEMBER),
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
    REVIEW_HIDE(AuditTargetType.MEMBER),
    REVIEW_UNHIDE(AuditTargetType.MEMBER),
    INQUIRY_HIDE(AuditTargetType.MEMBER),
    INQUIRY_UNHIDE(AuditTargetType.MEMBER),
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
    PRODUCT_DELETE(AuditTargetType.PRODUCT),
    PRODUCT_RESTORE(AuditTargetType.PRODUCT),
    /**
     * 관리자가 누르는 <b>주문 진행·판정</b> 넷 (2026-08-14). {@link #ORDER_CANCEL} 과 같은 자리를 메운다.
     *
     * <p>🔴 <b>넷 다 「언제」는 주문에 남고 「누가」는 아무 데도 안 남았다.</b> {@code shippedAt} ·
     * {@code deliveredAt} · {@code returnedAt} · {@code returnRejectedReason} 은 다 있는데
     * 행위자 컬럼을 가진 것은 취소({@code cancelledBy}) 하나뿐이고, 그건 감사도 함께 붙은 자리다.
     * 즉 <b>«누가 승인했나» 를 물을 방법이 없었다</b> — 돈이 나가는 조작인데도.
     *
     * <p>대상은 {@link #ORDER_CANCEL} 과 같이 <b>주문자(회원)</b> 이고 주문번호는 {@code detail} 에 넣는다.
     * 상품과 달리 «대상에 회원이 있느냐» 를 넘으므로 {@code target_type} 논의가 여기서는 안 생긴다.
     *
     * <p><b>detail 에 무엇을 적나</b> — 그 조작이 <b>무엇을 움직였는지</b>를 적는다.
     * 발송은 택배사·송장, 배송완료는 <b>나간 적립금</b>, 반품 승인은 <b>환불액</b>, 거절은 <b>사유</b>다.
     * ⚠ 거절 사유는 {@code return_rejected_reason} 에도 남지만 <b>그건 현재 상태</b>다 —
     * 재요청이 오면 지워진다({@code requestReturn} 이 null 로 되돌린다). 원장 쪽이 이력이다.
     *
     * <p>⚠ <b>빈도가 높은 것을 알고 넣었다</b>(2026-08-14 사용자와 확정). 발송·배송완료는 <b>모든 주문</b>이
     * 거치므로 원장이 주문 수에 비례해 큰다(63주문 → 최대 126행). F-2 와 같은 압력이고,
     * 그때 정리 대상은 <b>알림이지 원장이 아니다</b> — 원장은 append-only 가 존재 이유다.
     *
     * <p>⚠ <b>멱등 논의가 여기서는 안 생긴다</b>({@link #PRODUCT_DELETE} 와 다른 점). 넷 다 상태 가드가
     * <b>예외를 던진다</b>({@code isShippable}·{@code isDeliverable}·{@code isReturnPending}) —
     * 조용히 통과하는 경로가 없으므로 «호출됐지만 아무 일도 안 일어난» 줄이 생길 수 없다.
     *
     * <p>🔴 <b>과거 조작은 백필하지 않는다</b> — 시각은 있어도 <b>행위자를 모른다</b>.
     * 원장은 오늘부터 시작한다(V44·V50 과 같은 판단 — 모르는 값은 지어내지 않는다).
     *
     * <p>⚠ {@code V51} 로 CHECK 제약을 함께 넓혔다.
     */
    ORDER_SHIP(AuditTargetType.MEMBER),
    ORDER_DELIVER(AuditTargetType.MEMBER),
    ORDER_RETURN_APPROVE(AuditTargetType.MEMBER),
    ORDER_RETURN_REJECT(AuditTargetType.MEMBER),
    /**
     * 상품 <b>등록·수정</b> (2026-08-20). {@code V50} 이 미뤄 둔 자리를 메운다.
     *
     * <p>🔴 <b>V50 이 미룬 이유 둘이 오늘 답을 얻었다:</b>
     * ① *"빈도가 높아 원장이 빠르게 큰다"* → 실측 <b>상품 10건</b>이다. 주문 63건에 붙인
     *    {@code ORDER_SHIP} 보다 훨씬 작다 — 그때 감수한 압력보다 작은 것을 못 감수할 이유가 없다.
     * ② *"«무엇이 바뀌었나» 를 detail 에 어떻게 적을지 따로 정해야 한다"* → <b>바뀐 것만 «전→후»</b>
     *    로 적는다(2026-08-20 사용자와 확정).
     *
     * <p><b>detail 규칙</b>(구현은 {@code ProductCommandService.describeChanges}):
     * <ul>
     *   <li>안 바뀐 필드는 <b>안 적는다</b> — 매번 전부 적으면 «무엇이 바뀌었나» 가 안 읽힌다.</li>
     *   <li>긴 필드(설명·태그라인)는 <b>«바뀜» 만</b> — 전/후를 다 적으면 {@code detail}(1000자)을 넘긴다.</li>
     *   <li>옵션은 <b>개수만</b>. 🔴 <b>재고는 안 적는다</b> — {@code stock_history}(B-19)가 이미
     *       «누가·언제·얼마나» 를 갖고 있다. 같은 사실을 두 곳에 적으면 한쪽만 고쳐져 어긋난다.</li>
     * </ul>
     *
     * <p>⚠ <b>바뀐 것이 하나도 없어도 줄을 남긴다</b>(2026-08-20 사용자와 확정, detail 은 «변경 없음»).
     * 관리 화면이 상품 전체를 다시 보내므로 흔한 경우인데, <b>«누가 언제 손댔나» 자체를 접근 기록으로
     * 본다</b>는 선택이다. 🔴 {@link #PRODUCT_DELETE} 의 멱등 판단과 <b>갈리는 지점</b>이다 —
     * 거기는 조작이 <b>아무 일도 안 한</b> 경우였고 여기는 <b>실제로 저장까지 간</b> 경우다.
     *
     * <p>⚠ {@code V53} 으로 CHECK 제약을 함께 넓혔다.
     */
    PRODUCT_CREATE(AuditTargetType.PRODUCT),
    PRODUCT_UPDATE(AuditTargetType.PRODUCT),
    /**
     * 쿠폰 <b>정의 등록</b> · 관리자 <b>수동 발급</b> · <b>가입 쿠폰 지정</b> (2026-08-20).
     *
     * <p>⚠ <b>{@code COUPON_ISSUE} 만 대상이 회원이다</b> — 관리자가 «누구에게» 줬는지가 핵심이고,
     * 쿠폰명은 {@code detail} 에 넣는다. 나머지 둘은 대상이 <b>쿠폰 정의</b>다.
     *
     * <p>❌ <b>고객이 배너에서 누르는 이벤트 쿠폰 「받기」({@code claimEventCoupon})는 안 남긴다</b> —
     * 관리자 조작이 아니다. 원장은 «관리자가 무엇을 했나» 다(V51 이 본인 취소를 뺀 것과 같다).
     *
     * <p>⚠ 쿠폰에는 <b>삭제가 없다</b>(서비스에 그런 조작 자체가 없다). 값도 만들지 않는다 —
     * 안 일어나는 일에 값을 만들면 «왜 한 번도 안 쌓이지» 를 나중에 되짚게 된다.
     *
     * <p>⚠ {@code V53} 으로 CHECK 제약을 함께 넓혔다.
     */
    COUPON_CREATE(AuditTargetType.COUPON),
    COUPON_ISSUE(AuditTargetType.MEMBER),
    COUPON_WELCOME_SET(AuditTargetType.COUPON),
    /**
     * 기간 할인(타임세일) <b>등록·수정·삭제</b> (2026-08-20, G-5 의 후속).
     *
     * <p>🔴 <b>세일은 돈을 바꾸는 관리자 조작이다.</b> 실증(2026-08-20): 세일 20% 와 쿠폰 5,000 이
     * 겹쳐 정가 12,000 짜리가 <b>7,600 에 나갔는데 원장에 한 줄도 없었다.</b>
     *
     * <p>⚠ <b>대상은 할인이 아니라 상품이다.</b> 할인 id 는 사람에게 의미가 없고, 궁금한 것은
     * «어느 상품의 세일인가» 다. 덕분에 상품 수정·삭제와 <b>같은 {@code target_id} 로 묶인다</b>
     * ({@link AuditTargetType} 참조).
     *
     * <p><b>detail</b> 은 «20% · 08-20~08-22» 처럼 <b>할인율과 기간</b>이다 — 그 조작이 실제로
     * 정한 것이 그 둘이다. 수정은 {@link #PRODUCT_UPDATE} 와 같이 <b>바뀐 것만 «전→후»</b>.
     *
     * <p>⚠ <b>저절로 시작·종료하는 순간은 남지 않는다</b> — 그때는 아무도 아무것도 누르지 않았다.
     * 원장은 «관리자가 무엇을 했나» 이지 «무슨 일이 일어났나» 가 아니다(purge 를 안 남긴 것과 같은 결).
     *
     * <p>⚠ {@code V53} 으로 CHECK 제약을 함께 넓혔다.
     */
    DISCOUNT_CREATE(AuditTargetType.PRODUCT),
    DISCOUNT_UPDATE(AuditTargetType.PRODUCT),
    DISCOUNT_DELETE(AuditTargetType.PRODUCT);

    private final AuditTargetType targetType;

    AuditAction(AuditTargetType targetType) {
        this.targetType = targetType;
    }

    /** 이 조작의 {@code target_id} 가 무엇의 id 인지. 🔴 이벤트로 받지 않고 여기서 답한다(V53). */
    public AuditTargetType targetType() {
        return targetType;
    }
}
