package com.glassvue.global.config;

import java.time.Duration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

/**
 * Redis 캐시 설정. 값은 JSON으로 저장(타입 정보 포함해 역직렬화 가능), 기본 TTL 60초.
 *
 * <p>Spring Data Redis 4.0에서 Jackson 2 기반 {@code GenericJackson2JsonRedisSerializer}가
 * removal 예정으로 deprecated 되어, Jackson 3 기반 {@link GenericJacksonJsonRedisSerializer}로 이전했다.
 * Jackson 3는 java.time·record 지원이 내장이고 날짜 기본이 ISO-8601이라, 예전처럼 JavaTimeModule을
 * 따로 등록하거나 WRITE_DATES_AS_TIMESTAMPS를 끌 필요가 없다.
 *
 * <p><b>⚠ 캐시 포맷 변경</b>: Jackson 2 시절 포맷은 스칼라(UUID/Instant/enum)를 {@code ["타입","값"]}로
 * 감쌌지만 Jackson 3 포맷은 그렇지 않다. 두 포맷은 서로 역직렬화되지 않으므로, 이 버전을 배포할 때
 * {@code products:list}·{@code notices:list} 캐시를 flush 해야 한다(안 하면 배포 직후 최대 60초간
 * 구버전이 쓴 값이 역직렬화 실패로 500). TTL이 60초라 flush 없이 감수해도 창은 짧다.
 * 포맷 왕복(round-trip) 정확성은 CacheSerializationTest가 고정한다.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        // 캐시 값은 PageResponse<ProductResponse> 등 record 안에 record/enum/Instant가 중첩된 형태다.
        // deserialize(byte[])가 구체 타입을 복원하려면 값에 타입 정보(@class)가 실려 있어야 하므로
        // default typing을 켠다. 내부에서 쓰고 읽는 신뢰 캐시라 임의 타입을 허용한다(기존과 동일한 신뢰 모델).
        GenericJacksonJsonRedisSerializer serializer = GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(
                        BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object.class).build())
                .build();

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(60))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));
    }
}
