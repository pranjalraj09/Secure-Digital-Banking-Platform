package com.fincore.nexus.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;

import static com.fincore.nexus.service.AccountService.BALANCE_CACHE_NAME;

@Configuration
public class CacheConfig {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    @Primary
    @ConditionalOnProperty(name = "banking.cache.type", havingValue = "redis", matchIfMissing = false)
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        log.info("Configuring Redis Cache Manager for banking platform");
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(15))
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .withCacheConfiguration(BALANCE_CACHE_NAME, config.entryTtl(Duration.ofMinutes(10)))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "banking.cache.type", havingValue = "memory", matchIfMissing = true)
    public CacheManager memoryCacheManager() {
        log.info("Configuring In-Memory ConcurrentMapCacheManager for local development/fallback");
        return new ConcurrentMapCacheManager(BALANCE_CACHE_NAME);
    }
}
