package com.rehab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rehab.common.JwtUtil;
import com.rehab.config.AuthProperties;
import com.rehab.config.BusinessException;
import com.rehab.mapper.StudentMapper;
import com.rehab.mapper.TeacherMapper;
import com.rehab.pojo.entity.Student;
import com.rehab.pojo.entity.Teacher;
import com.rehab.pojo.vo.LoginVO;
import com.rehab.utils.RedisKeys;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 管理短期 Access Token 与 Redis 会话。
 * Refresh Token 是高熵随机值，只通过 HttpOnly Cookie 下发，Redis 中只保存其 SHA‑256 摘要；
 * 刷新时会轮换 refreshToken（旧token失效），退出或禁用账号时会删除整个会话，支持多设备登录、服务端主动踢下线。
 */
@Service
public class AuthTokenService {

    /**
     * 安全随机数，用于生成refreshToken
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Redis Lua脚本：原子轮换refreshToken，防止重放攻击
     * KEYS[1]：旧refreshKey（旧refreshToken哈希对应的key）
     * KEYS[2]：sessionKey（会话key）
     * KEYS[3]：新refreshKey（新refreshToken哈希对应的key）
     * ARGV[1]：sessionId
     * ARGV[2]：序列化后的session json字符串
     * ARGV[3]：TTL毫秒数 PX单位
     * 返回：1=轮换成功；0=失败（旧token已被使用/会话不存在）
     */
    private static final DefaultRedisScript<Long> ROTATE_REFRESH_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) ~= ARGV[1] then return 0 end " +
                    "if redis.call('exists', KEYS[2]) == 0 then return 0 end " +
                    "redis.call('del', KEYS[1]) " +
                    "redis.call('set', KEYS[3], ARGV[1], 'PX', ARGV[3]) " +
                    "redis.call('set', KEYS[2], ARGV[2], 'PX', ARGV[3]) return 1",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AuthProperties properties;
    private final TeacherMapper teacherMapper;
    private final StudentMapper studentMapper;

    public AuthTokenService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                            AuthProperties properties, TeacherMapper teacherMapper,
                            StudentMapper studentMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.teacherMapper = teacherMapper;
        this.studentMapper = studentMapper;
    }

    /**
     * 创建全新登录会话，颁发refreshToken与accessToken
     * @param id 用户id
     * @param name 用户姓名
     * @param role 用户角色 student / teacher
     * @return AuthResult 令牌结果
     */
    public AuthResult issue(Long id, String name, String role) {
        // 校验配置的过期时间参数合法性
        validateTtlConfiguration();
        // 生成全局唯一会话ID，一台设备对应一个sessionId
        String sessionId = UUID.randomUUID().toString();
        // 生成高熵随机refreshToken原始字符串，下发给浏览器Cookie
        String refreshToken = newRefreshToken();
        // 会话绝对过期时间：会话硬天花板，无论怎么刷新，到时间强制下线
        long absoluteExpiresAt = System.currentTimeMillis()
                + TimeUnit.DAYS.toMillis(properties.getMaxSessionTtlDays());
        // 组装会话对象，存入refreshToken的哈希，不存原始token
        Session session = new Session(id, name, role, hash(refreshToken), absoluteExpiresAt);
        // 计算refreshToken的存活毫秒数
        long refreshTtlMillis = remainingRefreshTtlMillis(session);

        // Redis写入会话JSON
        redisTemplate.opsForValue().set(sessionKey(sessionId), write(session), refreshTtlMillis, TimeUnit.MILLISECONDS);
        // refreshToken哈希映射sessionId，后续刷新时通过cookie里的refreshToken哈希反向找到sessionId
        redisTemplate.opsForValue().set(refreshKey(session.getRefreshHash()), sessionId,
                refreshTtlMillis, TimeUnit.MILLISECONDS);

        // 将sessionId加入用户会话集合，用于批量踢下线
        rememberUserSession(session, sessionId);
        // 组装返回结果
        return result(session, sessionId, refreshToken);
    }

    /**
     * refreshToken令牌刷新，滚动轮换机制，旧refreshToken直接作废
     * @param refreshToken 从Cookie读取的原始refreshToken
     * @return AuthResult 新的令牌结果
     */
    public AuthResult refresh(String refreshToken) {
        // refreshToken为空校验
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            throw BusinessException.unauthorized("refresh token is missing");
        }
        validateTtlConfiguration();

        // 对传入的refreshToken做sha256哈希，用哈希查询Redis
        String oldHash = hash(refreshToken);
        String oldRefreshKey = refreshKey(oldHash);
        String sessionId = redisTemplate.opsForValue().get(oldRefreshKey);
        Session session = readSession(sessionId);

        // 会话不存在 或者 哈希不匹配，直接删除旧key，抛出令牌无效
        if (session == null || !oldHash.equals(session.getRefreshHash())) {
            redisTemplate.delete(oldRefreshKey);
            throw BusinessException.unauthorized("refresh token is invalid or expired");
        }

        // 判断会话是否到达绝对最大生命周期
        if (session.getAbsoluteExpiresAt() <= System.currentTimeMillis()) {
            logout(sessionId);
            throw BusinessException.unauthorized("maximum session lifetime has expired");
        }

        // 校验账号状态：账号禁用则撤销全部会话
        if (!accountIsActive(session)) {
            revokeUserSessions(session.getId(), session.getRole());
            throw BusinessException.unauthorized("refresh token is invalid or expired");
        }

        // 生成全新refreshToken
        String nextRefreshToken = newRefreshToken();
        // 更新会话内保存的refresh哈希
        session.setRefreshHash(hash(nextRefreshToken));
        long refreshTtlMillis = remainingRefreshTtlMillis(session);

        // Lua脚本原子执行轮换，防止重放攻击
        Long rotated = redisTemplate.execute(ROTATE_REFRESH_SCRIPT,
                Arrays.asList(oldRefreshKey, sessionKey(sessionId), refreshKey(session.getRefreshHash())),
                sessionId, write(session), String.valueOf(refreshTtlMillis));

        // 返回值不为1，说明旧token已经被使用过，判定为重放攻击
        if (!Long.valueOf(1L).equals(rotated)) {
            throw BusinessException.unauthorized("refresh token has already been used");
        }

        rememberUserSession(session, sessionId);
        return result(session, sessionId, nextRefreshToken);
    }

    /**
     * 单会话登出：销毁指定sessionId对应的会话
     * @param sessionId 会话id
     */
    public void logout(String sessionId) {
        Session session = readSession(sessionId);
        if (session == null) {
            return;
        }
        // 删除session、refresh映射key
        redisTemplate.delete(Arrays.asList(sessionKey(sessionId), refreshKey(session.getRefreshHash())));
        // 用户会话集合中移除该sessionId
        redisTemplate.opsForSet().remove(userSessionsKey(session.getId(), session.getRole()), sessionId);
    }

    /**
     * 撤销该用户该角色下全部会话，实现一键踢所有设备下线
     * @param id 用户id
     * @param role 用户角色
     */
    public void revokeUserSessions(Long id, String role) {
        String userKey = userSessionsKey(id, role);
        Set<String> sessionIds = redisTemplate.opsForSet().members(userKey);
        if (sessionIds == null || sessionIds.isEmpty()) {
            redisTemplate.delete(userKey);
            return;
        }
        List<String> keys = new ArrayList<>();
        for (String sessionId : sessionIds) {
            keys.add(sessionKey(sessionId));
            Session session = readSession(sessionId);
            if (session != null) {
                keys.add(refreshKey(session.getRefreshHash()));
            }
        }
        keys.add(userKey);
        // 批量删除所有会话、refresh映射、用户会话集合
        redisTemplate.delete(keys);
    }

    /**
     * 组装认证返回结果AuthResult，登录、刷新令牌逻辑共用
     * @param session 用户会话对象
     * @param sessionId 会话唯一id
     * @param refreshToken 原始刷新令牌字符串
     * @return 封装好的AuthResult，包含前端响应VO、refreshToken、refresh过期秒数
     */
    private AuthResult result(Session session, String sessionId, String refreshToken) {
        // accessToken实际有效毫秒数：取【配置的access过期时间】和【会话绝对过期时间剩余时长】两者的较小值
        // 会话临近硬截止时间时，accessToken会被缩短，不会再下发完整配置时长
        long accessTtlMillis = Math.min(TimeUnit.MINUTES.toMillis(properties.getAccessTokenTtlMinutes()),
                session.getAbsoluteExpiresAt() - System.currentTimeMillis());

        // 会话已经到达最大绝对生命周期，禁止生成accessToken，强制重新登录
        if (accessTtlMillis <= 0) {
            throw BusinessException.unauthorized("maximum session lifetime has expired");
        }

        // 生成JWT格式accessToken，载荷携带用户id、角色、sessionId、有效时长
        // 携带sessionId用于服务端校验会话是否被吊销，解决原生JWT无法注销的问题
        String accessToken = JwtUtil.createAccessToken(session.getId(), session.getRole(), sessionId, accessTtlMillis);

        // 组装返回前端的登录VO，作为http响应body的数据
        // accessExpiresIn：accessToken剩余有效秒数，供前端做过期倒计时、触发刷新逻辑
        LoginVO login = new LoginVO(session.getId(), session.getName(), session.getRole(), accessToken,
                TimeUnit.MILLISECONDS.toSeconds(accessTtlMillis));

        // 计算refreshToken剩余存活秒数，用于设置Cookie的Max‑Age属性
        long refreshExpiresInSeconds = TimeUnit.MILLISECONDS.toSeconds(remainingRefreshTtlMillis(session));

        // 封装AuthResult对外返回，上层respond方法会把refreshToken写入HttpOnly Cookie，login返回JSON响应体
        return new AuthResult(login, refreshToken, refreshExpiresInSeconds);
    }

    /**
     * 将sessionId记录到用户会话集合，用于后续批量踢下线
     * @param session 会话
     * @param sessionId 会话id
     */
    private void rememberUserSession(Session session, String sessionId) {
        String key = userSessionsKey(session.getId(), session.getRole());
        redisTemplate.opsForSet().add(key, sessionId);
        // 集合可能包含同一用户的多个会话，使用最长会话期限避免某个临近到期的会话
        // 把其他较新的会话索引提前删除。无效成员在撤销或自然过期后按需清理。
        redisTemplate.expire(key, properties.getMaxSessionTtlDays(), TimeUnit.DAYS);
    }

    /**
     * 校验账号状态是否正常，学生/教师分别查询数据库
     * @param session 会话
     * @return true账号正常；false账号禁用/角色变更
     */
    private boolean accountIsActive(Session session) {
        if ("student".equals(session.getRole())) {
            Student student = studentMapper.getById(session.getId());
            return student != null && Integer.valueOf(1).equals(student.getStatus());
        }
        Teacher teacher = teacherMapper.getById(session.getId());
        String currentRole = teacher == null || teacher.getRole() == null ? "teacher" : teacher.getRole();
        return teacher != null && Integer.valueOf(1).equals(teacher.getStatus())
                && session.getRole().equals(currentRole);
    }

    /**
     * 从Redis读取并反序列化Session会话
     * @param sessionId 会话id
     * @return Session对象；读取失败/不存在返回null
     */
    private Session readSession(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return null;
        }
        String json = redisTemplate.opsForValue().get(sessionKey(sessionId));
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Session.class);
        } catch (Exception e) {
            // 序列化异常直接删除脏key
            redisTemplate.delete(sessionKey(sessionId));
            return null;
        }
    }

    /**
     * 将Session序列化为JSON字符串存入Redis
     * @param session 会话对象
     * @return json字符串
     */
    private String write(Session session) {
        try {
            return objectMapper.writeValueAsString(session);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize auth session", e);
        }
    }

    /**
     * 校验yml配置的TTL参数，必须大于0
     */
    private void validateTtlConfiguration() {
        if (properties.getAccessTokenTtlMinutes() <= 0 || properties.getRefreshTokenTtlDays() <= 0
                || properties.getMaxSessionTtlDays() <= 0) {
            throw new IllegalStateException("auth token TTL must be greater than zero");
        }
    }

    /**
     * 计算refreshToken剩余存活毫秒数
     * 取refresh空闲TTL 和会话绝对剩余时间的较小值
     * @param session 会话
     * @return ttl毫秒
     */
    private long remainingRefreshTtlMillis(Session session) {
        long idleTtlMillis = TimeUnit.DAYS.toMillis(properties.getRefreshTokenTtlDays());
        long absoluteRemainingMillis = session.getAbsoluteExpiresAt() - System.currentTimeMillis();
        long ttlMillis = Math.min(idleTtlMillis, absoluteRemainingMillis);
        if (ttlMillis <= 0) {
            throw BusinessException.unauthorized("maximum session lifetime has expired");
        }
        return ttlMillis;
    }

    /**
     * 生成32字节高熵refreshToken，URL安全base64无padding
     * @return refreshToken原始字符串
     */
    private static String newRefreshToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 对token做SHA‑256哈希，URL安全base64编码
     * @param token 原始token字符串
     * @return sha256摘要
     */
    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /**
     * 组装session的redis key
     */
    private static String sessionKey(String sessionId) {
        return RedisKeys.AUTH_SESSION_KEY + sessionId;
    }

    /**
     * 组装refresh哈希映射的redis key
     */
    private static String refreshKey(String refreshHash) {
        return RedisKeys.AUTH_REFRESH_KEY + refreshHash;
    }

    /**
     * 用户会话集合key：按角色+用户id隔离
     */
    private static String userSessionsKey(Long id, String role) {
        return RedisKeys.AUTH_USER_SESSIONS_KEY + role + ":" + id;
    }

    /**
     * Redis存储的会话内部实体
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class Session {
        private Long id;                 // 用户id
        private String name;             // 用户姓名
        private String role;             // 用户角色 student/teacher
        private String refreshHash;      // refreshToken sha256哈希
        private long absoluteExpiresAt;  // 会话绝对过期时间戳（硬天花板）
    }
}
