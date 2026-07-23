package com.glassvue.domain.order.entity;

import lombok.Getter;

/**
 * 택배사. 송장번호와 짝을 이뤄 주문의 배송 정보를 이룬다.
 *
 * <p><b>조회 URL은 여기 두지 않는다.</b> 처음엔 택배사별 실제 조회 URL을 이 enum에 박았는데,
 * 연습 단계에서 그건 두 가지가 잘못이다 — ①주문이 전부 가짜라 송장번호도 가짜여서 실제
 * 택배사 페이지로 보내봐야 "조회 결과 없음"만 나오고 ②통제할 수 없는 외부 의존이 생긴다
 * (택배사가 URL을 바꾸면 우리 화면의 링크가 조용히 깨지는데, 깨진 걸 알 방법이 없다).
 * ARCHITECTURE §1 "미리 만들지 않는다"에도 어긋난다.
 *
 * <p>→ 조회 URL은 <b>설정</b>({@code glassvue.delivery})으로 뺐고, 기본값은 <b>앱 안의 예시 페이지</b>다.
 * 실제 배송이 필요한 시점에 설정만 채우면 된다(PG 연동을 seam으로 남겨둔 것과 같은 방식).
 *
 * <p><b>DB에는 CHECK 제약을 걸지 않았다</b>(V13). 택배사는 앞으로 늘어날 값이라, CHECK를 걸면
 * 하나 추가할 때마다 제약 교체 마이그레이션을 써야 한다(V13에서 {@code orders.status}에 실제로 그 작업을 했다).
 * 값 검증은 이 enum이 한다 — 요청 역직렬화 단계에서 없는 값은 걸러진다.
 * 반대로 {@code OrderStatus}는 상태 전이 규칙이라 DB 레벨 보호를 유지한다.
 */
@Getter
public enum DeliveryCarrier {

    CJ("CJ대한통운"),
    KOREA_POST("우체국택배"),
    HANJIN("한진택배"),
    LOTTE("롯데택배"),
    LOGEN("로젠택배"),

    /** 직접 전달·기타. 조회할 택배사가 없으므로 링크를 만들지 않는다(화면은 송장번호만 보여준다). */
    ETC("기타");

    private final String displayName;

    DeliveryCarrier(String displayName) {
        this.displayName = displayName;
    }

    /** 조회 링크를 만들 수 있는 택배사인가. ETC는 조회할 곳 자체가 없다. */
    public boolean isTrackable() {
        return this != ETC;
    }
}
