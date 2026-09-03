package com.glassvue.domain.audit.entity;

/**
 * 감사 대상 관리자 조작의 종류. 회원·주문·리뷰·문의·상품·쿠폰·할인·카테고리·공지가
 * <b>한 테이블</b>에 남고 값으로만 구분한다.
 *
 * <p>🔴 <b>각 값은 자기 대상이 무엇인지도 안다</b>({@link #targetType()}, V53). 이벤트 파라미터로
 * 받지 않는 이유는 «action 은 {@code PRODUCT_UPDATE} 인데 targetType 은 {@code MEMBER}» 인 행을
 * <b>만들 수 없게</b> 하기 위해서다.
 *
 * <h2>대상을 고르는 원칙</h2>
 * ⚠ <b>«id 를 물어볼 값어치가 있는 쪽» 으로 잡는다.</b> 주문 조작의 대상이 주문이 아니라
 * <b>주문자</b>이고(V43), 할인 조작의 대상이 할인이 아니라 <b>상품</b>인(V53) 이유가 같다.
 * 덕분에 같은 회원·같은 상품의 서로 다른 조작이 <b>한 {@code target_id} 로 묶인다.</b>
 * {@link AuditTargetType} 에 없는 대상이면 <b>접지 말고 값을 더한다</b>(V56 이 카테고리로 처음 했다).
 *
 * <h2>원장에 남기는 것 · 안 남기는 것</h2>
 * 원장은 <b>«관리자가 무엇을 했나»</b> 다. 그래서 다음은 <b>일부러</b> 안 남긴다 —
 * 없는 것이 아니라 <b>안 남기기로 한 것</b>이라, 값을 더하려거든 이 줄을 먼저 읽는다:
 * <ul>
 *   <li><b>고객 본인의 조작</b> — 본인 취소, 고객의 부분 취소·반품 요청, 이벤트 쿠폰 「받기」.
 *       그 사실은 주문·주문품목 자신이 갖고 있다.</li>
 *   <li><b>사람이 안 누른 것</b> — 상품 영구삭제 배치(purge), 세일의 자동 시작·종료.
 *       {@code actor_id} 가 NOT NULL 인데 <b>지어낼 값이 아니다.</b></li>
 *   <li><b>조회</b> — 공지 조회수는 고객이 읽은 것이고 Redis 누적이라 트랜잭션도 없다.</li>
 * </ul>
 * ⚠ <b>안 일어나는 일에는 값을 만들지 않는다</b> — 쿠폰·카테고리에 「수정/삭제」 API 가 없어
 * 값도 없다. 만들어 두면 «왜 한 번도 안 쌓이지» 를 나중에 되짚게 된다.
 *
 * <h2>과거는 백필하지 않는다</h2>
 * 🔴 <b>각 값의 원장은 그 값이 생긴 날부터 시작한다.</b> 이전 조작은 «누가·언제» 를 남긴 곳이
 * 없어 백필할 출처 자체가 없다(현재 상태만 아는 컬럼은 그 답을 못 준다).
 * <b>모르는 값은 지어내지 않는다</b> — V37 동의 시각 이후 V44·V50·V51·V56 이 같은 판단을 했다.
 *
 * <h2>detail 공통 규칙</h2>
 * <ul>
 *   <li><b>바뀐 것만 «전→후»</b>. 안 바뀐 필드는 안 적는다 — 전부 적으면 무엇이 바뀌었는지 안 읽힌다.</li>
 *   <li><b>긴 필드(설명·태그라인·공지 본문)는 «바뀜» 만</b> — 전/후를 다 실으면 상한을 넘긴다.</li>
 *   <li><b>바뀐 것이 없어도 줄을 남긴다</b>(«변경 없음»). 관리 화면이 전체를 다시 보내 흔한 경우인데,
 *       «누가 언제 손댔나» 자체를 <b>접근 기록</b>으로 본다.</li>
 *   <li><b>삭제는 지우기 전에 읽는다</b> — 뒤에서 읽으면 적을 값이 없다.</li>
 *   <li>🔴 <b>다른 원장이 이미 가진 것은 안 적는다</b> — 재고는 {@code stock_history}(B-19) 가
 *       «누가·언제·얼마나» 를 갖고 있다.</li>
 *   <li>⚠ 상한(1000자)을 넘기면 <b>{@code AdminAuditLog} 가 자른다</b> — 도메인에서 자르지 않는다.
 *       넘긴 채로 두면 {@code ORA-12899} 로 <b>조작 자체가 롤백된다.</b></li>
 * </ul>
 *
 * <h2>값을 더할 때 (⚠ 셋 다 자동으로 안 따라온다)</h2>
 * <ol>
 *   <li>🔴 <b>CHECK 제약을 새 {@code V<n>} 으로 함께 넓힌다</b> — {@code action} 은 문자열로 저장되고
 *       (V32) {@code ddl-auto=validate} 라 제약은 절대 안 따라온다(Oracle enum CHECK 트랩).
 *       넓히는 방향이라 구 jar 는 영향받지 않는다. 대상을 더했으면 {@code target_type} 쪽도 함께다.</li>
 *   <li>🔴 <b>이름 길이를 센다</b> — {@code action} 열이 {@code VARCHAR2(20 CHAR)} 다.
 *       {@code ORDER_RETURN_REQUEST}·{@code ORDER_RETURN_APPROVE} 가 <b>정확히 20자</b>라 여유가 없다.</li>
 *   <li>대상이 {@link AuditTargetType} 에 없으면 그쪽에 값을 더한다.</li>
 * </ol>
 *
 * <p>경위(왜 이 순서로 늘었나 · 그날의 실측)는 핸드오프에 있다 —
 * {@code handoffs/2026-08-10}(주문·콘텐츠) · {@code 2026-08-14}(상품·주문 진행) ·
 * {@code 2026-08-20}(상품 등록수정·쿠폰·할인) · {@code 2026-08-21}(카테고리·공지·문의답변) ·
 * {@code 2026-08-27}(§I-15 대행 반품요청).
 */
public enum AuditAction {
    /** 회원 정지. */
    MEMBER_SUSPEND(AuditTargetType.MEMBER),
    /** 회원 정지 해제. */
    MEMBER_UNSUSPEND(AuditTargetType.MEMBER),
    /** 회원 역할 변경(USER↔ADMIN). */
    MEMBER_ROLE_CHANGE(AuditTargetType.MEMBER),
    /** 회원 강제 삭제(B-24). 되돌릴 수 없는 조작이라 <b>SUPER_ADMIN 전용</b>이다. V35. */
    MEMBER_DELETE(AuditTargetType.MEMBER),
    /**
     * 관리자 대행 주문 <b>전체</b> 취소(B-25). 대상은 <b>주문자</b>, 주문번호는 {@code detail} 에.
     * V43.
     */
    ORDER_CANCEL(AuditTargetType.MEMBER),
    /**
     * 관리자가 주문의 <b>일부만</b> 취소한다 (G-4). V57.
     *
     * <p>🔴 <b>{@link #ORDER_CANCEL} 과 나누는 이유는 돈이 다르게 움직이기 때문이다.</b> 전체 취소는
     * 결제금액 전부가 돌아가고 쿠폰도 복구되지만, 부분 취소는 <b>몫을 나눠</b> 돌려주고
     * <b>쿠폰은 그대로 걸려 있다</b>(G-4 결정 1 — 최소금액을 소급하지 않는다).
     * 한 행동으로 묶으면 원장만 보고는 둘을 못 가른다.
     *
     * <p><b>detail</b>: 주문번호 · 품목명 · 수량 · 환불액. 🔴 <b>그 회차로 주문이 끝났으면
     * «(마지막 품목 — 주문 취소됨)»</b> (I-6). ⚠ <b>마지막 회차에만</b> 붙인다 — 매 회차에 붙이면
     * «품목 하나 뺐다» 와 구분이 안 된다. 회수된 쿠폰·적립금 몫은 안 적는다(주문 행에 누적돼 있다).
     *
     * <p>⚠ <b>여기가 부분 취소의 유일한 회차별 이력이다.</b> {@code order_item.cancelled_at} 은
     * 마지막 시각만 들고 있어 같은 품목을 두 번 나눠 취소하면 앞선 것이 덮인다.
     */
    ORDER_ITEM_CANCEL(AuditTargetType.MEMBER),
    /**
     * 리뷰 숨김 / 해제 · 문의 숨김 / 해제 (B-18) — <b>콘텐츠 조치</b>. V44.
     *
     * <p>대상은 <b>작성자</b>({@code authorId}), 무엇을 숨겼는지는 {@code detail}(제목·상품명)에 넣는다.
     * ⚠ 제목은 <b>지금 값</b>이라 나중에 수정되면 어긋날 수 있는데, 그래도 id 보다 낫다.
     */
    REVIEW_HIDE(AuditTargetType.MEMBER),
    REVIEW_UNHIDE(AuditTargetType.MEMBER),
    INQUIRY_HIDE(AuditTargetType.MEMBER),
    INQUIRY_UNHIDE(AuditTargetType.MEMBER),
    /**
     * 상품 삭제 대기(F-7 의 soft delete) / 되돌리기. V50.
     *
     * <p>🔴 <b>«대상에 회원이 있느냐» 를 처음으로 못 넘은 값이다</b> — 상품에는 회원이 없다.
     * 그래서 {@code targetId} 의 뜻을 <b>«대상의 id, 그게 무엇인지는 이 action 이 말한다»</b> 로
     * 넓혔다. {@code targetLogin} 은 {@code null} 이고 상품명은 {@code detail} 에 넣는다.
     *
     * <p>⚠ <b>대가를 하나 안다</b>: 감사 화면의 검색은 {@code targetLogin} 부분일치뿐이라
     * ({@code AdminAuditLogRepository}) 상품 행은 <b>「조작 종류」 필터로만</b> 걸리고
     * 「대상 아이디」 열은 빈칸이다.
     */
    PRODUCT_DELETE(AuditTargetType.PRODUCT),
    PRODUCT_RESTORE(AuditTargetType.PRODUCT),
    /**
     * 관리자가 누르는 <b>주문 진행·판정</b> 넷. 대상은 <b>주문자</b>, 주문번호는 {@code detail} 에.
     * V51 · {@code ORDER_RETURN_REQUEST} 만 V60.
     *
     * <p>🔴 <b>넷 다 「언제」는 주문에 남고 「누가」는 아무 데도 안 남았다</b> — 행위자 컬럼을 가진
     * 것은 취소({@code cancelledBy}) 하나뿐이었다. 돈이 나가는 조작인데도 «누가 승인했나» 를
     * 물을 방법이 없었다.
     *
     * <p><b>detail 에는 그 조작이 움직인 것을 적는다</b> — 발송은 택배사·송장, 배송완료는
     * <b>나간 적립금</b>, 반품 승인은 <b>환불액 + 사유</b>(I-10), 거절은 <b>사유</b>다.
     * ⚠ 거절 사유는 {@code return_rejected_reason} 에도 있지만 <b>그건 현재 상태</b>다 —
     * 재요청이 오면 {@code requestReturn} 이 null 로 되돌린다. 원장 쪽이 이력이다.
     * ⚠ 승인 사유도 같은 이유다: {@code order.return_reason} 은 <b>한 칸</b>이라 다음 요청이 덮으므로
     * «1회차는 왜 반품했나» 를 알 곳이 <b>여기뿐</b>이다.
     * ⚠ 🔴 <b>사유를 «앞» 에 적는다</b> — 품목 목록이 길면 뒤부터 잘린다(§I-13 결정 3).
     *
     * <p>⚠ <b>빈도가 높은 것을 알고 넣었다</b>. 발송·배송완료는 <b>모든 주문</b>이 거치므로 원장이
     * 주문 수에 비례해 큰다. 압력이 오면 <b>정리 대상은 알림이지 원장이 아니다</b> —
     * 원장은 append-only 가 존재 이유다.
     *
     * <p>⚠ <b>멱등 논의가 여기서는 안 생긴다</b>({@link #PRODUCT_DELETE} 와 다른 점). 넷 다 상태
     * 가드가 <b>예외를 던져</b>({@code isShippable}·{@code isDeliverable}·{@code isReturnPending})
     * «호출됐지만 아무 일도 안 일어난» 줄이 생길 수 없다.
     */
    ORDER_SHIP(AuditTargetType.MEMBER),
    ORDER_DELIVER(AuditTargetType.MEMBER),
    /**
     * 🔴 <b>관리자가 «대신» 건 반품 요청</b> (§I-15). V60.
     *
     * <p>반품 요청은 원래 <b>고객만</b> 하는 일이라 남길 이유가 없었다. §I-9 이 7일 기한을 걸면서
     * <b>기한을 넘긴 건을 구제할 자리</b>로 대행 경로가 생겼고, 그 순간 «관리자가 대신 요청했다» 가
     * 물어볼 값어치가 있는 사실이 된다.
     *
     * <p>🔴 <b>이 값이 붙는 요청은 «기한을 넘긴 것일 수 있다»</b>(§I-15 결정 1) — 그래서 detail 에
     * <b>기한 경과 여부를 적는다.</b> 원장만 보는 사람이 «왜 34일 지난 주문이 반품됐나» 를 물을 때
     * 답이 거기 있어야 한다.
     */
    ORDER_RETURN_REQUEST(AuditTargetType.MEMBER),
    ORDER_RETURN_APPROVE(AuditTargetType.MEMBER),
    ORDER_RETURN_REJECT(AuditTargetType.MEMBER),
    /**
     * 상품 <b>등록·수정</b>. V53. 구현은 {@code ProductCommandService.describeChanges}.
     *
     * <p>V50 이 «빈도가 높아 원장이 빠르게 큰다» 며 미뤄 둔 자리다 — <b>실측 상품 10건</b>이라
     * 주문 63건에 붙인 {@link #ORDER_SHIP} 보다 작아서 감수했다.
     *
     * <p>⚠ <b>옵션은 개수만</b> 적는다. 🔴 <b>재고는 안 적는다</b> — 공통 규칙 참조.
     *
     * <p>🔴 <b>「변경 없음」도 남기는 것이 {@link #PRODUCT_DELETE} 의 멱등 판단과 갈리는 지점</b>이다 —
     * 거기는 조작이 <b>아무 일도 안 한</b> 경우였고 여기는 <b>실제로 저장까지 간</b> 경우다.
     */
    PRODUCT_CREATE(AuditTargetType.PRODUCT),
    PRODUCT_UPDATE(AuditTargetType.PRODUCT),
    /**
     * 쿠폰 <b>정의 등록</b> · 관리자 <b>수동 발급</b> · <b>가입 쿠폰 지정</b>. V53.
     *
     * <p>⚠ <b>{@code COUPON_ISSUE} 만 대상이 회원이다</b> — 관리자가 «누구에게» 줬는지가 핵심이고
     * 쿠폰명은 {@code detail} 에 넣는다. 나머지 둘은 대상이 <b>쿠폰 정의</b>다.
     */
    COUPON_CREATE(AuditTargetType.COUPON),
    COUPON_ISSUE(AuditTargetType.MEMBER),
    COUPON_WELCOME_SET(AuditTargetType.COUPON),
    /**
     * 기간 할인(타임세일) <b>등록·수정·삭제</b> (G-5 의 후속). V53.
     *
     * <p>🔴 <b>세일은 돈을 바꾸는 관리자 조작이다</b> — 붙이기 전에는 세일과 쿠폰이 겹쳐
     * 정가 12,000 짜리가 7,600 에 나가도 원장에 <b>한 줄도 없었다</b>(2026-08-20 실증).
     *
     * <p>⚠ <b>대상은 할인이 아니라 상품이다</b> — 이유는 {@link AuditTargetType} 에 있다.
     *
     * <p><b>detail</b> 은 «20% · 08-20~08-22» 처럼 <b>할인율과 기간</b>이다 — 그 조작이 실제로 정한 것.
     */
    DISCOUNT_CREATE(AuditTargetType.PRODUCT),
    DISCOUNT_UPDATE(AuditTargetType.PRODUCT),
    DISCOUNT_DELETE(AuditTargetType.PRODUCT),
    /**
     * 카테고리 <b>등록·삭제</b>. V56 — ⚠ {@code action} 과 {@code target_type} <b>둘 다</b> 넓힌 첫 값이다.
     *
     * <p>⚠ <b>삭제는 되돌릴 수 없다</b> — soft delete 가 없다({@code deleteById}). 상품이 하나라도
     * 붙어 있으면 막히므로({@code CATEGORY_IN_USE}) 지워지는 것은 늘 빈 카테고리지만, 이름을 다시
     * 만들어도 <b>id 가 달라져</b> 예전 것과 같은 것이 아니다.
     *
     * <p><b>detail 은 카테고리명.</b>
     */
    CATEGORY_CREATE(AuditTargetType.CATEGORY),
    CATEGORY_DELETE(AuditTargetType.CATEGORY),
    /**
     * 공지 <b>등록·수정·삭제</b>. V56.
     *
     * <p>✅ 선행 조건은 E-4 가 풀었다 — 그전에는 공지를 <b>고객도 쓸 수 있어</b> «관리자 조작» 이
     * 아니었다. 🔴 <b>기능 하나가 닫히면서 비로소 뜻을 갖게 된 값</b>이다.
     *
     * <p>⚠ <b>삭제는 되돌릴 수 없다</b>({@code repository.delete}) — 상품(F-7)과 갈리는 지점이고,
     * 그래서 <b>제목이 detail 에 남는 것이 유일한 흔적</b>이다.
     *
     * <p><b>detail</b>: 등록·삭제는 제목, 수정은 바뀐 것만 «전→후». 🔴 <b>본문은 «바뀜» 만</b> —
     * 공지 본문은 상품 설명보다 길어 전/후를 다 실으면 상한을 확실히 넘긴다.
     */
    NOTICE_CREATE(AuditTargetType.NOTICE),
    NOTICE_UPDATE(AuditTargetType.NOTICE),
    NOTICE_DELETE(AuditTargetType.NOTICE),
    /**
     * 문의 <b>답변 등록·수정</b>. V56.
     *
     * <p>⚠ <b>대상은 문의가 아니라 질문자</b>다({@link #INQUIRY_HIDE} 와 같은 판단) — 같은 회원의
     * «숨김» 과 «답변» 이 한 {@code target_id} 로 묶인다.
     *
     * <p>🔴 <b>이 조작은 고객에게 알림이 나간다</b>(B-15). 되돌릴 수 없는 것은 답변이 아니라
     * <b>알림</b>이다 — 답변은 고쳐 쓸 수 있지만 <b>이미 나간 알림은 회수할 수 없다.</b>
     * 그래서 detail 에 <b>«첫 답변인가» 를 적는다</b>: 알림은 첫 답변에만 나가므로
     * (수정마다 보내면 오타 고칠 때마다 알림이 간다) <b>그 한 조각이 «알림이 나갔나» 의 답</b>이다.
     *
     * <p>⚠ <b>값이 하나다</b>(등록·수정을 안 가른다) — 갈라도 얻는 것이 없다. 궁금한 것은
     * «알림이 나갔나» 인데 그건 detail 이 이미 답한다.
     */
    INQUIRY_ANSWER(AuditTargetType.MEMBER);

    private final AuditTargetType targetType;

    AuditAction(AuditTargetType targetType) {
        this.targetType = targetType;
    }

    /** 이 조작의 {@code target_id} 가 무엇의 id 인지. 🔴 이벤트로 받지 않고 여기서 답한다(V53). */
    public AuditTargetType targetType() {
        return targetType;
    }
}
