package com.rehab.utils;

public class RedisKeys {
    public static final String CACHE_COURSE_KEY = "cache:course:";
    public static final String CACHE_COURSE_LIST_KEY = "cache:course:list";
    public static final String CACHE_SCHEDULE_KEY = "cache:schedule:";
    public static final String CACHE_TEACHER_LIST_KEY = "cache:teacher:list";
    public static final String CACHE_NULL_VALUE = "";
    public static final String APPOINTMENT_STOCK_KEY = "appointment:schedule:stock:";
    public static final String APPOINTMENT_STATUS_KEY = "appointment:status:";
    public static final String APPOINTMENT_OWNER_KEY = "appointment:owner:";
    public static final String AUTH_SESSION_KEY = "auth:session:";
    public static final String AUTH_REFRESH_KEY = "auth:refresh:";
    public static final String AUTH_USER_SESSIONS_KEY = "auth:user-sessions:";

    public static final long CACHE_TTL_MINUTES = 30L;
    public static final long CACHE_NULL_TTL_MINUTES = 2L;
    public static final long APPOINTMENT_STATUS_TTL_HOURS = 24L;

    private RedisKeys() {
    }
}
