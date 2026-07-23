package com.glassvue.domain.order.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 택배사 enum. 조회 URL은 여기 없다 — 설정({@code glassvue.delivery})으로 뺐다.
 * 여기서 고정하는 건 표시명과 "조회할 곳이 있는 택배사인가"뿐이다.
 */
class DeliveryCarrierTest {

    @Test
    @DisplayName("모든 택배사가 표시명을 갖는다 — 화면이 enum 이름을 그대로 노출하지 않게")
    void everyCarrierHasDisplayName() {
        for (DeliveryCarrier c : DeliveryCarrier.values()) {
            assertThat(c.getDisplayName()).isNotBlank();
        }
    }

    @Test
    @DisplayName("기타(ETC)는 조회할 택배사가 없다 — 직접 전달 등")
    void etcIsNotTrackable() {
        assertThat(DeliveryCarrier.ETC.isTrackable()).isFalse();
    }

    @Test
    @DisplayName("나머지 택배사는 조회 대상이다")
    void othersAreTrackable() {
        for (DeliveryCarrier c : DeliveryCarrier.values()) {
            if (c != DeliveryCarrier.ETC) {
                assertThat(c.isTrackable()).as(c.name()).isTrue();
            }
        }
    }
}
