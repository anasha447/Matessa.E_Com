package com.matessa.commercial.config;

import com.matessa.commercial.tenant.TenantContext;
import com.matessa.commercial.tenant.TenantProperties;

import com.matessa.commercial.dto.response.CategoryResponse;
import com.matessa.commercial.dto.response.ProductResponse;
import com.matessa.commercial.dto.response.ProductVariantResponse;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Redis cache for the read-heavy catalog domain (Phase 2). Cache-aside via {@code @Cacheable};
 * writes evict in the owning service methods (see {@code @CacheEvict}) so an added or edited
 * product appears on the very next read — no TTL wait. Keys are prefixed {@code cat:v1:}.
 *
 * <p>Each cache uses a <b>type-bound</b> JSON serializer built from Boot's ObjectMapper (which
 * carries the JavaTime module, so {@code Instant} fields serialize). Type-bound serializers avoid
 * the polymorphic-typing / final-record pitfalls of a generic serializer — every cache holds
 * exactly one shape.
 *
 * <p><b>Not cached on purpose:</b> {@code getPriceStock} (the checkout stock-validation path).
 * Caching decrementable stock would reintroduce the oversell race the {@code @Version} optimistic
 * lock prevents. Only display-side reads are cached; variant lists get a short TTL.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    /** Product read-model by slug (the PDP fetch). */
    public static final String PRODUCT = "product";
    /** Variants for a product (includes display price/stock — short TTL). */
    public static final String VARIANT_LIST = "variantList";
    /** Category tree / top-level (near-static). */
    public static final String CATEGORIES = "categories";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper,
                                         TenantProperties tenants) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                // Store-scoped keys: cat:v1:<store>:<cache>::<key>. Evaluated per operation, so each
                // store reads/evicts only its own entries.
                .computePrefixWith(cacheName -> "cat:v1:" + currentStore(tenants) + ":" + cacheName + "::")
                .disableCachingNullValues();

        JavaType variantListType = objectMapper.getTypeFactory()
                .constructCollectionType(List.class, ProductVariantResponse.class);
        JavaType categoryListType = objectMapper.getTypeFactory()
                .constructCollectionType(List.class, CategoryResponse.class);

        Map<String, RedisCacheConfiguration> perCache = Map.of(
                PRODUCT, withValue(base.entryTtl(Duration.ofMinutes(10)),
                        new Jackson2JsonRedisSerializer<>(objectMapper, ProductResponse.class)),
                VARIANT_LIST, withValue(base.entryTtl(Duration.ofSeconds(30)),
                        new Jackson2JsonRedisSerializer<>(objectMapper, variantListType)),
                CATEGORIES, withValue(base.entryTtl(Duration.ofHours(1)),
                        new Jackson2JsonRedisSerializer<>(objectMapper, categoryListType))
        );

        // SCAN, not KEYS, when an eviction clears a whole cache: KEYS blocks Redis for every
        // client while it walks the keyspace. transactionAware: evictions inside a database
        // transaction happen after it commits, so a concurrent read cannot re-cache the
        // pre-commit value for a whole TTL.
        return RedisCacheManager.builder(RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory, BatchStrategies.scan(500)))
                .cacheDefaults(base.entryTtl(Duration.ofMinutes(5)))
                .withInitialCacheConfigurations(perCache)
                .transactionAware()
                .build();
    }

    /**
     * The cache is an optimisation: when Redis is down, reads fall through to Postgres and
     * writes skip the cache instead of failing the request with a 500. A programming error
     * (no store in context) is still thrown.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, org.springframework.cache.Cache cache, Object key) {
                tolerate(e, "get", cache);
            }

            @Override
            public void handleCachePutError(RuntimeException e, org.springframework.cache.Cache cache, Object key, Object value) {
                tolerate(e, "put", cache);
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, org.springframework.cache.Cache cache, Object key) {
                tolerate(e, "evict", cache);
            }

            @Override
            public void handleCacheClearError(RuntimeException e, org.springframework.cache.Cache cache) {
                tolerate(e, "clear", cache);
            }
        };
    }

    private static void tolerate(RuntimeException e, String op, org.springframework.cache.Cache cache) {
        if (e instanceof IllegalStateException) {
            throw e;
        }
        org.slf4j.LoggerFactory.getLogger(CacheConfig.class)
                .warn("Cache {} on '{}' failed, serving without cache: {}", op, cache.getName(), e.toString());
    }

    /** Same rule as the Hibernate tenant resolver: no store in context is a bug, not a default. */
    private static String currentStore(TenantProperties tenants) {
        String store = TenantContext.getTenantId();
        if (store == null || !tenants.isKnown(store)) {
            throw new IllegalStateException("Cache access outside a store context");
        }
        return store;
    }

    private static RedisCacheConfiguration withValue(RedisCacheConfiguration cfg, Jackson2JsonRedisSerializer<?> ser) {
        return cfg.serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(ser));
    }
}
