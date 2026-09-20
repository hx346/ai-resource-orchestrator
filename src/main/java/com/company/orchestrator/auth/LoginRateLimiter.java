package com.company.orchestrator.auth;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 登录失败限速：按「用户名|来源 IP」记滑动窗口，达到阈值后锁定一段时间
 * （锁定至最后一次失败起 lock-minutes）。单体内存实现，无需 Redis；阈值 0 关闭。
 * In-memory sliding-window login throttling keyed by username|IP for this
 * modular monolith; max-failures 0 disables the limiter entirely.
 */
@Component
public class LoginRateLimiter {

    private final int maxFailures;
    private final long windowMillis;
    private final ConcurrentHashMap<String, Deque<Long>> failures = new ConcurrentHashMap<>();

    public LoginRateLimiter(@Value("${app.auth.login-max-failures:5}") int maxFailures,
            @Value("${app.auth.login-lock-minutes:15}") int lockMinutes) {
        this.maxFailures = maxFailures;
        this.windowMillis = lockMinutes * 60_000L;
    }

    public boolean enabled() { return maxFailures > 0; }

    public void recordFailure(String key) {
        if (!enabled()) return;
        failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        var stamps = failures.get(key);
        synchronized (stamps) { stamps.addLast(System.currentTimeMillis()); }
    }

    public boolean blocked(String key) {
        if (!enabled()) return false;
        var stamps = failures.get(key);
        if (stamps == null) return false;
        long cutoff = System.currentTimeMillis() - windowMillis;
        synchronized (stamps) {
            stamps.removeIf(t -> t < cutoff);
            return stamps.size() >= maxFailures;
        }
    }

    public void reset(String key) { failures.remove(key); }

    /** 限速键：用户名 + 来源 IP（可信代理时取 X-Forwarded-For 首段）/ throttle key: username + client IP. */
    public static String key(String username, String ip) {
        return (username == null ? "" : username.toLowerCase()) + "|" + (ip == null ? "" : ip);
    }

    public static String clientIp(jakarta.servlet.http.HttpServletRequest request) {
        var forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
