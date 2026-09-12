package com.rehab.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordUtilTest {
    @Test
    void newPasswordsUseBcrypt() {
        String encoded = PasswordUtil.encode("strong-password");

        assertTrue(encoded.startsWith("$2"));
        assertTrue(PasswordUtil.matches("strong-password", encoded));
        assertFalse(PasswordUtil.matches("wrong", encoded));
    }

    @Test
    void plaintextPasswordsAreRejected() {
        assertFalse(PasswordUtil.matches("123456", "123456"));
    }

    @Test
    void legacySha256PasswordsCanBeMigratedOnLogin() {
        String legacy = "sha256$AQIDBAUGBwgJCgsMDQ4PEA==$oBRtYRvZ0r29ANGqBrzkgFAPzmB5DguRbZle9eo0saM=";

        assertTrue(PasswordUtil.matches("123456", legacy));
        assertTrue(PasswordUtil.needsUpgrade(legacy));
    }
}
