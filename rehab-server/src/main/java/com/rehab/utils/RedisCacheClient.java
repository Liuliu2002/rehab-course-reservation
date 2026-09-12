package com.rehab.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 通用 Cache Aside 客户端。
 *
 * <p>读取顺序为 Redis -> MySQL -> 回填 Redis；写操作由业务服务先更新数据库再删除缓存。
 * 对不存在的数据缓存短 TTL 空值，用于缓解缓存穿透。该实现没有处理缓存击穿，面试时可说明
 * 热点 key 可进一步使用互斥锁、逻辑过期或主动刷新。</p>
 */
@Component
public class RedisCacheClient {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    public <T> T queryObject(String key, Class<T> type, Supplier<T> dbFallback) {
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json != null) {
            // 空字符串代表“数据库也查不到”，短时间缓存空值可以防止缓存穿透。
            if (RedisKeys.CACHE_NULL_VALUE.equals(json)) {
                return null;
            }
            return readObject(json, type);
        }

        T data = dbFallback.get();
        if (data == null) {
            // 查询不到数据也缓存一小段时间，避免恶意/异常请求反复打到数据库。
            stringRedisTemplate.opsForValue().set(key, RedisKeys.CACHE_NULL_VALUE,
                    RedisKeys.CACHE_NULL_TTL_MINUTES, TimeUnit.MINUTES);
            return null;
        }
        write(key, data, RedisKeys.CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        return data;
    }

    public <T> List<T> queryList(String key, TypeReference<List<T>> type, Supplier<List<T>> dbFallback) {
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json != null) {
            // 列表查询同样使用空值缓存；前端拿到空列表即可正常展示“暂无数据”。
            if (RedisKeys.CACHE_NULL_VALUE.equals(json)) {
                return java.util.Collections.emptyList();
            }
            return readList(json, type);
        }

        List<T> data = dbFallback.get();
        if (data == null || data.isEmpty()) {
            stringRedisTemplate.opsForValue().set(key, RedisKeys.CACHE_NULL_VALUE,
                    RedisKeys.CACHE_NULL_TTL_MINUTES, TimeUnit.MINUTES);
            return java.util.Collections.emptyList();
        }
        write(key, data, RedisKeys.CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        return data;
    }

    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }

    private void write(String key, Object value, long time, TimeUnit unit) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), time, unit);
        } catch (Exception e) {
            throw new RuntimeException("failed to write redis cache", e);
        }
    }

    private <T> T readObject(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new RuntimeException("failed to read redis cache", e);
        }
    }

    private <T> List<T> readList(String json, TypeReference<List<T>> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new RuntimeException("failed to read redis cache", e);
        }
    }
}
