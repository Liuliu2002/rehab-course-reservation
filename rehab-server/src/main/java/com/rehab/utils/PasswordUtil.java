package com.rehab.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码编码兼容层。
 *
 * <p>新账号使用 BCrypt（自带随机盐和计算成本）；旧 SHA-256 格式只负责迁移期校验，
 * 登录成功后由 AuthServiceImpl 自动重新编码为 BCrypt。明文密码永远不会被视为合法哈希。</p>
 */
public class PasswordUtil {
    private static final String PREFIX = "sha256";
    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();

    private PasswordUtil() {
    }

    public static String encode(String rawPassword) {
        return BCRYPT.encode(rawPassword);
    }

    public static boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }
        if (storedPassword.startsWith("$2a$") || storedPassword.startsWith("$2b$") || storedPassword.startsWith("$2y$")) {
            return BCRYPT.matches(rawPassword, storedPassword);
        }
        if (!storedPassword.startsWith(PREFIX + "$")) {
            return false;
        }
        try {
            String[] parts = storedPassword.split("\\$");
            if (parts.length != 3) {
                return false;
            }
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            return MessageDigest.isEqual(
                    digest(rawPassword, salt).getBytes(StandardCharsets.UTF_8),
                    parts[2].getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean needsUpgrade(String storedPassword) {
        return storedPassword != null && !storedPassword.startsWith("$2a$")
                && !storedPassword.startsWith("$2b$") && !storedPassword.startsWith("$2y$");
    }

    private static String digest(String rawPassword, byte[] salt) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update(salt);
            byte[] hash = messageDigest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("failed to encode password", e);
        }
    }
}
