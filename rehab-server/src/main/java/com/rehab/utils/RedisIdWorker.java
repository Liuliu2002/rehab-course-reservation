package com.rehab.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 基于 Redis 自增序列生成趋势递增的 64 位 ID。
 * 高 32 位保存相对时间，低 32 位保存当天序号，因此多个应用实例共享 Redis 时仍不会重复。
 * 预约在发送 RabbitMQ 消息前就需要 ID，所以不能依赖 MySQL 自增主键。
 */
@Component
public class RedisIdWorker {
    // 分布式 ID 结构：高 32 位是相对时间戳，低 32 位是当天自增序列。
    // 优点：趋势递增、无需依赖数据库自增主键，适合先生成预约 ID 再发送 MQ 消息。
    private static final long BEGIN_TIMESTAMP = 1704067200L;
    private static final int COUNT_BITS = 32;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix) {
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        return timestamp << COUNT_BITS | count;
    }
}
