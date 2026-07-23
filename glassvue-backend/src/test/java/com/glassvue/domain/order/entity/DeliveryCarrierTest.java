package com.glassvue.domain.order.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 택배사 → 배송조회 URL 생성. 서버가 완성된 링크를 주기로 한 규약을 고정한다
 * (화면이 택배사별 URL 형식을 알지 않게 하는 것이 목적).
 */
class DeliveryCarrierTest {

    @Test
    @DisplayName("송장번호를 URL 형식에 끼워 넣는다")
    void buildsTrackingUrl() {
        assertThat(DeliveryCarrier.CJ.trackingUrl("123456789012"))
                .isEqualTo("https://trace.cjlogistics.co.kr/next/tracking.html?wblNo=123456789012");
        assertThat(DeliveryCarrier.LOGEN.trackingUrl("999"))
                .isEqualTo("https://www.ilogen.com/web/personal/trace/999");
    }

    @Test
    @DisplayName("조회 형식이 없는 택배사(기타)는 링크가 null — 화면은 송장번호만 보여준다")
    void etcHasNoUrl() {
        assertThat(DeliveryCarrier.ETC.getTrackingUrlFormat()).isNull();
        assertThat(DeliveryCarrier.ETC.trackingUrl("123")).isNull();
    }

    @Test
    @DisplayName("송장번호가 없거나 비면 링크도 없다 — 형식만으로 빈 조회 URL을 만들지 않는다")
    void noTrackingNoMeansNoUrl() {
        assertThat(DeliveryCarrier.CJ.trackingUrl(null)).isNull();
        assertThat(DeliveryCarrier.CJ.trackingUrl("")).isNull();
        assertThat(DeliveryCarrier.CJ.trackingUrl("  ")).isNull();
    }

    @Test
    @DisplayName("모든 택배사가 표시명을 갖는다 — 화면이 enum 이름을 그대로 노출하지 않게")
    void everyCarrierHasDisplayName() {
        for (DeliveryCarrier c : DeliveryCarrier.values()) {
            assertThat(c.getDisplayName()).isNotBlank();
        }
    }
}
