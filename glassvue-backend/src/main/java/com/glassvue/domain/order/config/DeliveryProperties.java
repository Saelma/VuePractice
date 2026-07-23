package com.glassvue.domain.order.config;

import com.glassvue.domain.order.entity.DeliveryCarrier;
import java.util.EnumMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 배송 조회 링크 설정({@code glassvue.delivery}).
 *
 * <p><b>왜 설정으로 뺐나</b> — 처음엔 택배사별 실제 조회 URL을 {@code DeliveryCarrier} enum에 박았다.
 * 연습 단계에서 실제 택배사 사이트를 가리키는 건 의미도 없고(송장번호가 가짜라 "조회 결과 없음"만 나온다)
 * 통제할 수 없는 외부 의존만 생긴다(택배사가 URL을 바꾸면 링크가 조용히 깨지고, 깨진 걸 알 방법이 없다).
 *
 * <p>기본값은 <b>앱 안의 예시 페이지</b>(`/mock-tracking`)다. 실제 배송이 필요해지면
 * {@code tracking-url} 아래에 택배사별 실제 URL을 넣기만 하면 된다 — 코드 변경 없이.
 *
 * <p>자리표시자: <code>{trackingNo}</code>, <code>{carrier}</code>.
 * 실제 택배사 URL은 보통 송장번호만 쓰므로 <code>{trackingNo}</code> 하나만 넣으면 된다.
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "glassvue.delivery")
public class DeliveryProperties {

    /** 택배사별 조회 URL 형식. 여기 값이 있으면 {@link #defaultTrackingUrl}보다 우선한다. */
    private Map<DeliveryCarrier, String> trackingUrl = new EnumMap<>(DeliveryCarrier.class);

    /** 택배사별 설정이 없을 때 쓰는 형식. 비우면 링크를 만들지 않는다. */
    private String defaultTrackingUrl = "";

    /**
     * 조회 링크를 만든다. 만들 수 없으면 null — 화면은 null이면 링크를 감추고 송장번호만 보여준다.
     *
     * <p>null이 되는 경우: 택배사·송장번호가 없거나, ETC(조회할 곳이 없다)거나, 형식 설정이 비어 있을 때.
     */
    public String resolve(DeliveryCarrier carrier, String trackingNo) {
        if (carrier == null || trackingNo == null || trackingNo.isBlank() || !carrier.isTrackable()) {
            return null;
        }
        String format = trackingUrl.get(carrier);
        if (format == null || format.isBlank()) {
            format = defaultTrackingUrl;
        }
        if (format == null || format.isBlank()) {
            return null;
        }
        return format
                .replace("{trackingNo}", trackingNo)
                .replace("{carrier}", carrier.name());
    }
}
