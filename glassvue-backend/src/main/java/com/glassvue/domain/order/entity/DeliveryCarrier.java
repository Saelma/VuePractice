package com.glassvue.domain.order.entity;

import lombok.Getter;

/**
 * 택배사. 송장번호와 짝을 이뤄 배송 추적 링크를 만든다.
 *
 * <p><b>왜 enum인가</b> — 조회 URL 형식이 택배사마다 달라서, 어딘가에는 "택배사 → URL" 지식이 있어야 한다.
 * 그걸 프론트에 두면 화면마다 중복되고 백엔드는 검증할 근거가 없어진다. 서버가 갖고 있다가
 * 응답에 완성된 {@code trackingUrl}을 실어 주면 화면은 그냥 링크만 걸면 된다.
 *
 * <p><b>DB에는 CHECK 제약을 걸지 않았다</b>(V13). 택배사는 앞으로 늘어날 값이라, CHECK를 걸면
 * 하나 추가할 때마다 제약 교체 마이그레이션을 써야 한다(V13에서 {@code orders.status}에 실제로 그 작업을 했다).
 * 값 검증은 이 enum이 한다 — 요청 역직렬화 단계에서 없는 값은 걸러진다.
 * 반대로 {@code OrderStatus}는 상태 전이 규칙이라 DB 레벨 보호를 유지한다.
 *
 * <p>⚠ 조회 URL은 <b>외부 사이트의 형식</b>이라 예고 없이 바뀔 수 있다. 링크가 깨지면 여기만 고치면 된다.
 * 링크가 죽어도 송장번호 자체는 화면에 그대로 보이므로 고객이 직접 조회할 수는 있다.
 */
@Getter
public enum DeliveryCarrier {

    CJ("CJ대한통운", "https://trace.cjlogistics.co.kr/next/tracking.html?wblNo=%s"),
    KOREA_POST("우체국택배", "https://service.epost.go.kr/trace.RetrieveDomRigiTraceList.comm?sid1=%s"),
    HANJIN("한진택배", "https://www.hanjin.com/kor/CMS/DeliveryMgr/WaybillResult.do?mCode=MN038&schLang=KR&wblnumText2=%s"),
    LOTTE("롯데택배", "https://www.lotteglogis.com/home/reservation/tracking/linkView?InvNo=%s"),
    LOGEN("로젠택배", "https://www.ilogen.com/web/personal/trace/%s"),

    /** 직접 전달·기타. 조회 링크가 없으므로 화면은 송장번호만 보여준다. */
    ETC("기타", null);

    private final String displayName;
    private final String trackingUrlFormat;

    DeliveryCarrier(String displayName, String trackingUrlFormat) {
        this.displayName = displayName;
        this.trackingUrlFormat = trackingUrlFormat;
    }

    /** 조회 링크. 형식이 없는 택배사(ETC)이거나 송장번호가 없으면 null — 화면은 링크를 감춘다. */
    public String trackingUrl(String trackingNo) {
        if (trackingUrlFormat == null || trackingNo == null || trackingNo.isBlank()) {
            return null;
        }
        return trackingUrlFormat.formatted(trackingNo);
    }
}
