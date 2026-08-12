package com.glassvue.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.glassvue.domain.catalog.dto.ProductResponse;
import com.glassvue.domain.catalog.dto.VariantResponse;
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
                "매일 쓰기 좋은 기본형",   // tagline — null 아닌 값으로 둬서 왕복에 실제로 포함시킨다(V33)
                "설명",
                12000L,
                39000L,   // listPrice — 정가가 있는 상태도 직렬화 왕복에 포함시킨다
                List.of(new VariantResponse(
                        UUID.fromString("019f7d1c-e0b4-7000-8000-00000000000a"),
                        "기본", 0, 12000L, 7L, false)),
                7L,      // totalStock
                false,   // soldOut
                ProductStatus.SELLING,
                UUID.fromString("019f7d1c-e0b4-7000-8000-000000000002"),
                "카테고리",
                List.of(new ImageResponse(
                        UUID.fromString("019f7d1c-e0b4-7000-8000-000000000003"),
                        "/uploads/a.png", "/uploads/a_m.webp", "/uploads/a_t.webp")),
                4.5,
                2L,
                123L,    // soldCount
                false,   // deleted — 캐시에 실리는 목록은 **살아 있는 상품뿐**이다(F-7, 2026-08-12)
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
        assertThat(p.tagline()).isEqualTo("매일 쓰기 좋은 기본형"); // V33 — 새 필드도 왕복에 실린다
    }

    /**
     * ⚠ <b>필드를 추가하면 이미 쌓인 캐시가 있다</b>(WORKING-AGREEMENTS §2-7).
     * {@code products:list} 에는 tagline 이 <b>없던 시절의 값</b>이 남아 있고, 배포 직후 신 코드가 그걸 읽는다.
     *
     * <p>그래서 <b>옛 캐시 값을 실제로 만들어</b>(직렬화 후 tagline 키를 지워) 신 코드로 읽어 본다.
     * "없는 필드는 null 이 되겠지"를 추측하지 않고 바이트로 확인하는 것이 §2-7 의 요구다.
     *
     * <p>반대 방향(구 jar 이 tagline 이 <b>있는</b> 값을 읽는 것)은 <b>롤백할 때만</b> 발생한다 —
     * 배포는 구 jar 를 내리고 신 jar 를 올리므로 동시에 뜨지 않는다. 되돌릴 일이 생기면
     * 캐시를 flush 해야 한다(핸드오프 배포 절에 적어 둔다).
     */
    @Test
    void tagline이_없던_옛_캐시값도_읽힌다() {
        GenericJacksonJsonRedisSerializer serializer = serializer();

        String json = new String(serializer.serialize(samplePayload()), java.nio.charset.StandardCharsets.UTF_8);
        String oldFormat = json.replace("\"tagline\":\"매일 쓰기 좋은 기본형\",", ""); // V33 이전 모양
        assertThat(oldFormat).doesNotContain("tagline");

        Object restored = serializer.deserialize(oldFormat.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        @SuppressWarnings("unchecked")
        PageResponse<ProductResponse> page = (PageResponse<ProductResponse>) restored;
        ProductResponse p = page.content().get(0);
        assertThat(p.tagline()).isNull();          // 없는 필드는 null
        assertThat(p.name()).isEqualTo("지바");     // 나머지는 멀쩡하다
        assertThat(p.price()).isEqualTo(12000L);
    }
}
