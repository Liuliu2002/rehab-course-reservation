package com.rehab.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rehab.auth")
public class AuthProperties {
    private long accessTokenTtlMinutes = 15L;
    private long refreshTokenTtlDays = 7L;
    private long maxSessionTtlDays = 30L;
    private boolean refreshCookieSecure;
}
