package com.glassvue.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.glassvue.domain.catalog.dto.ProductResponse;
import com.glassvue.domain.catalog.entity.ProductStatus;
import com.glassvue.domain.image.dto.ImageResponse;
import com.glassvue.global.response.PageResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

/**
 * 캐시 직렬화기(Jackson 3 {@link GenericJacksonJsonRedisSerializer})가 실제 캐시 값 타입
 * (PageResponse&lt;ProductResponse&gt;)을 <b>타입 정보를 잃지 않고 왕복</b>하는지 고정한다.
 *
 * <p>@Cacheable이 값을 다시 읽을 때는 대상 타입 없이 {@code deserialize(byte[])}만 호출되므로,
 * 값에 타입 정보(@class)가 실려 있어야 record/enum/Instant가 원래 타입으로 복원된다.
 * 이게 깨지면 캐시 히트가 ClassCastException/500으로 이어진다.
 *
 * <p>CacheConfig와 동일한 방식으로 직렬화기를 구성한다(설정이 어긋나면 이 테스트가 먼저 깨지게).
 */
class CacheSerializationTest {

    private static GenericJacksonJsonRedisSerializer serializer() {
        return GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(
                        BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object.class).build())
                .build();
    }

    private static PageResponse<ProductResponse> samplePayload() {
        ProductResponse p = new ProductResponse(
                UUID.fromString("019f7d1c-e0b4-7000-8000-000000000001"),
                "지바",
                "설명",
                12000L,
                7L,
                ProductStatus.SELLING,
                UUID.fromString("019f7d1c-e0b4-7000-8000-000000000002"),
                "카테고리",
                List.of(new ImageResponse(
                        UUID.fromString("019f7d1c-e0b4-7000-8000-000000000003"),
                        "/uploads/a.png")),
                4.5,
                2L,
                Instant.parse("2026-07-20T12:34:56Z"),
                Instant.parse("2026-07-20T13:00:00Z"));
        return new PageResponse<>(List.of(p), 0, 20, 1L, 1, true);
    }

    @Test
    void PageResponse가_타입을_잃지_않고_왕복한다() {
        GenericJacksonJsonRedisSerializer serializer = serializer();

        byte[] bytes = serializer.serialize(samplePayload());
        Object restored = serializer.deserialize(bytes);

        assertThat(restored).isInstanceOf(PageResponse.class);
        @SuppressWarnings("unchecked")
        PageResponse<ProductResponse> page = (PageResponse<ProductResponse>) restored;
        assertThat(page.totalElements()).isEqualTo(1L);
        assertThat(page.content()).hasSize(1);

        ProductResponse p = page.content().get(0);
        assertThat(p).isInstanceOf(ProductResponse.class);
        assertThat(p.name()).isEqualTo("지바");
        assertThat(p.status()).isEqualTo(ProductStatus.SELLING);
        assertThat(p.averageRating()).isEqualTo(4.5);
        assertThat(p.id()).isEqualTo(UUID.fromString("019f7d1c-e0b4-7000-8000-000000000001"));
        assertThat(p.createdAt()).isEqualTo(Instant.parse("2026-07-20T12:34:56Z"));
        assertThat(p.images()).hasSize(1);
        assertThat(p.images().get(0).url()).isEqualTo("/uploads/a.png");
    }
}
