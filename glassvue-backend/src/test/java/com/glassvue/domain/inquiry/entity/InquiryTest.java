package com.glassvue.domain.inquiry.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link Inquiry} 생성 규칙 (2026-08-07, G-3 2단계).
 *
 * <p>🔴 <b>여기서 보는 것은 «유형과 상품이 짝을 이루는가» 하나다.</b> 둘은 같은 사실의 두 면인데
 * 컬럼이 둘로 나뉘어 있어, 한쪽만 틀린 행이 생길 수 있다. 그리고 그런 행은 <b>앱을 멈추지 않는다</b> —
 * 저장도 되고 목록도 뜨고 화면도 그려진다. 어긋남은 <b>엉뚱한 자리</b>에서만 드러난다:
 * <ul>
 *   <li>{@code PRODUCT} 인데 상품이 없으면 → 관리자 목록의 상품명이 영원히 «—»</li>
 *   <li>일반 유형인데 상품이 있으면 → 그 문의가 <b>상품 문의 목록에 섞여</b> 뜬다</li>
 * </ul>
 *
 * <p>DB 제약({@code ck_inquiry_product_pair})이 최종 방어선이지만 <b>단위 테스트로도 고정한다</b> —
 * DB 까지 가면 예외가 flush 시점에 터져 <b>어느 코드가 만들었는지</b> 알기 어렵고, DB 없이 도는
 * 단위 테스트에서는 아예 안 잡힌다.
 */
class InquiryTest {

    private static Inquiry.InquiryBuilder base() {
        return Inquiry.builder()
                .authorId(UUID.randomUUID()).author("nick")
                .title("t").content("c").secret(false);
    }

    @Nested
    @DisplayName("유형과 상품은 짝이다")
    class TypeProductPair {

        @Test
        @DisplayName("상품 문의: PRODUCT + 상품 있음 → 만들어진다")
        void product_withProduct_ok() {
            Inquiry i = base().type(InquiryType.PRODUCT).productId(UUID.randomUUID()).build();

            assertThat(i.getType()).isEqualTo(InquiryType.PRODUCT);
            assertThat(i.getProductId()).isNotNull();
            assertThat(i.getStatus()).as("새 문의는 미답변에서 시작한다").isEqualTo(InquiryStatus.WAITING);
        }

        @Test
        @DisplayName("일반 문의: 상품 없음 → 만들어진다")
        void general_withoutProduct_ok() {
            Inquiry i = base().type(InquiryType.DELIVERY).productId(null).build();

            assertThat(i.getType()).isEqualTo(InquiryType.DELIVERY);
            assertThat(i.getProductId()).as("일반 문의는 상품이 없다 — 이것이 2단계가 연 자리다").isNull();
        }

        @Test
        @DisplayName("🔴 PRODUCT 인데 상품이 없으면 거부된다(관리자 목록에서 상품명이 영원히 비는 행)")
        void product_withoutProduct_rejected() {
            assertThatThrownBy(() -> base().type(InquiryType.PRODUCT).productId(null).build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("어긋납니다");
        }

        @Test
        @DisplayName("🔴 일반 유형인데 상품이 있으면 거부된다(상품 문의 목록에 섞여 뜨는 행)")
        void general_withProduct_rejected() {
            assertThatThrownBy(() -> base().type(InquiryType.REFUND).productId(UUID.randomUUID()).build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("어긋납니다");
        }

        @Test
        @DisplayName("유형이 없으면 거부된다 — 기본값으로 때우지 않는다")
        void nullType_rejected() {
            // ⚠ productId 로 유형을 **추측**할 수도 있었다(있으면 PRODUCT). 안 그런 이유:
            //    추측이 맞는 한 아무도 빠뜨린 걸 모르고, 틀린 순간엔 이미 저장된 뒤다.
            assertThatThrownBy(() -> base().type(null).productId(UUID.randomUUID()).build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("필수");
        }

        @Test
        @DisplayName("일반 유형 전부가 상품 없이 만들어진다(PRODUCT 만 예외다)")
        void allGeneralTypes_ok() {
            for (InquiryType type : InquiryType.values()) {
                if (type.requiresProduct()) {
                    continue;
                }
                // ⚠ 값을 넉넉히 잡아 뒀으므로(V42) 화면에 아직 안 띄우는 PAYMENT·ACCOUNT 도 함께 돈다 —
                //   CHECK 제약과 enum 이 어긋나면 여기가 아니라 **저장 시점**에 ORA-02290 으로 터진다.
                assertThatCode(() -> base().type(type).productId(null).build())
                        .as("%s 는 상품 없이 만들어져야 한다", type)
                        .doesNotThrowAnyException();
            }
        }
    }

    @Test
    @DisplayName("requiresProduct 는 PRODUCT 에서만 참이다")
    void requiresProduct_onlyForProduct() {
        assertThat(InquiryType.PRODUCT.requiresProduct()).isTrue();
        assertThat(InquiryType.DELIVERY.requiresProduct()).isFalse();
        assertThat(InquiryType.REFUND.requiresProduct()).isFalse();
        assertThat(InquiryType.PAYMENT.requiresProduct()).isFalse();
        assertThat(InquiryType.ACCOUNT.requiresProduct()).isFalse();
        assertThat(InquiryType.ETC.requiresProduct()).isFalse();
    }
}
