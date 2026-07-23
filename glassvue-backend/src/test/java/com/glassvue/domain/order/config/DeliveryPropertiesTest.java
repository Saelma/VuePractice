package com.glassvue.domain.order.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.glassvue.domain.order.entity.DeliveryCarrier;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 배송 조회 링크 생성 규칙.
 *
 * <p>이 클래스가 있는 이유 자체가 "실제 택배사 URL을 코드에 박지 않기 위해서"다 —
 * 연습 단계에선 앱 안의 예시 페이지를 가리키고, 실서비스가 필요해지면 설정만 채운다.
 * 그 전환이 실제로 되는지(설정이 우선하는지)를 고정한다.
 */
class DeliveryPropertiesTest {

    private DeliveryProperties props(String defaultUrl, Map<DeliveryCarrier, String> perCarrier) {
        DeliveryProperties p = new DeliveryProperties();
        p.setDefaultTrackingUrl(defaultUrl);
        if (perCarrier != null) {
            p.setTrackingUrl(perCarrier);
        }
        return p;
    }

    @Test
    @DisplayName("기본 형식의 자리표시자를 채운다")
    void fillsPlaceholders() {
        DeliveryProperties p = props("/mock-tracking?carrier={carrier}&no={trackingNo}", null);
        assertThat(p.resolve(DeliveryCarrier.CJ, "123456789012"))
                .isEqualTo("/mock-tracking?carrier=CJ&no=123456789012");
    }

    @Test
    @DisplayName("택배사별 설정이 있으면 기본 형식보다 우선한다 (실서비스 전환 경로)")
    void perCarrierOverridesDefault() {
        DeliveryProperties p = props("/mock-tracking?carrier={carrier}&no={trackingNo}",
                Map.of(DeliveryCarrier.CJ, "https://example.test/track?wblNo={trackingNo}"));

        assertThat(p.resolve(DeliveryCarrier.CJ, "999"))
                .isEqualTo("https://example.test/track?wblNo=999");
        // 설정이 없는 택배사는 그대로 기본 형식을 쓴다
        assertThat(p.resolve(DeliveryCarrier.HANJIN, "999"))
                .isEqualTo("/mock-tracking?carrier=HANJIN&no=999");
    }

    @Test
    @DisplayName("기본 형식이 비어 있으면 링크를 만들지 않는다 — 화면이 링크를 감춘다")
    void blankConfigMeansNoLink() {
        assertThat(props("", null).resolve(DeliveryCarrier.CJ, "123")).isNull();
    }

    @Test
    @DisplayName("기타(ETC)는 조회할 곳이 없어 링크를 만들지 않는다")
    void etcHasNoLink() {
        DeliveryProperties p = props("/mock-tracking?carrier={carrier}&no={trackingNo}", null);
        assertThat(p.resolve(DeliveryCarrier.ETC, "123")).isNull();
    }

    @Test
    @DisplayName("택배사·송장번호가 없으면 링크도 없다 — 빈 조회 URL을 만들지 않는다")
    void missingValuesMeanNoLink() {
        DeliveryProperties p = props("/mock-tracking?carrier={carrier}&no={trackingNo}", null);
        assertThat(p.resolve(null, "123")).isNull();
        assertThat(p.resolve(DeliveryCarrier.CJ, null)).isNull();
        assertThat(p.resolve(DeliveryCarrier.CJ, "")).isNull();
        assertThat(p.resolve(DeliveryCarrier.CJ, "  ")).isNull();
    }
}
