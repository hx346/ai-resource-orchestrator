package com.company.orchestrator.auth;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 登录失败限速：按「用户名|来源 IP」记滑动窗口，达到阈值后锁定一段时间
 * （锁定至最后一次失败起 lock-minutes）。单体内存实现，无需 Redis；阈值 0 关闭。
 * In-memory sliding-window login throttling keyed by username|IP for this
 * modular monolith; max-failures 0 disables the limiter entirely.
 * IP 取直连地址；TRUSTED_PROXY=true 时才信 X-Forwarded-For 末段（首段客户端
 * 可伪造，轮换假 XFF 即可绕过限速，末段才是直连可信代理追加的真实来源）。
 * The client IP is the socket address unless a trusted proxy fronts the app,
 * in which case the LAST X-Forwarded-For hop (appended by that proxy) is used —
 * the first hop is client-controlled and would defeat throttling.
 */
@Slf4j
@Component
public class LoginRateLimiter {

    /** 键数上限：随机键攻击下宁可整体重置也不无界增长 / key cap: reset wholesale rather than grow unbounded. */
    private static final int MAX_KEYS = 10_000;

    private final int maxFailures;
    private final long windowMillis;
    private final boolean trustedProxy;
    private final ConcurrentHashMap<String, Deque<Long>> failures = new ConcurrentHashMap<>();

    public LoginRateLimiter(@Value("${app.auth.login-max-failures:5}") int maxFailures,
            @Value("${app.auth.login-lock-minutes:15}") int lockMinutes,
            @Value("${app.auth.trusted-proxy:false}") boolean trustedProxy) {
        this(maxFailures, lockMinutes * 60_000L, trustedProxy);
    }

    LoginRateLimiter(int maxFailures, long windowMillis, boolean trustedProxy) { // 毫秒窗口供单测 / millis window for tests
        this.maxFailures = maxFailures;
        this.windowMillis = windowMillis;
        this.trustedProxy = trustedProxy;
    }

    public boolean enabled() { return maxFailures > 0; }

    public void recordFailure(String key) {
        if (!enabled()) return;
        if (failures.size() >= MAX_KEYS) {
            log.warn("login limiter key cap {} reached, resetting all windows", MAX_KEYS);
            failures.clear();
        }
        var stamps = failures.computeIfAbsent(key, k -> new ArrayDeque<>()); // 直接用返回值，避免与 reset 竞态 / use the return value; a separate get() can race reset()
        synchronized (stamps) { stamps.addLast(System.currentTimeMillis()); }
    }

    public boolean blocked(String key) {
        if (!enabled()) return false;
        var stamps = failures.get(key);
        if (stamps == null) return false;
        long cutoff = System.currentTimeMillis() - windowMillis;
        synchronized (stamps) {
            stamps.removeIf(t -> t < cutoff);
            if (stamps.isEmpty()) { failures.remove(key, stamps); return false; } // 空窗口即回收 / recycle empty windows
            return stamps.size() >= maxFailures;
        }
    }

    public void reset(String key) { failures.remove(key); }

    /** 锁定退避秒数（429 Retry-After）/ lockout seconds for the 429 Retry-After header. */
    public long lockSeconds() { return windowMillis / 1000; }

    /** 限速键：用户名 + 来源 IP / throttle key: username + client IP. */
    public static String key(String username, String ip) {
        return (username == null ? "" : username.toLowerCase()) + "|" + (ip == null ? "" : ip);
    }

    public String clientIp(jakarta.servlet.http.HttpServletRequest request) {
        return pickIp(request.getHeader("X-Forwarded-For"), request.getRemoteAddr());
    }

    /** 可信代理时取 XFF 末段（直连代理追加的真实来源），否则忽略可伪造的头 / last hop only behind a trusted proxy. */
    String pickIp(String forwarded, String remoteAddr) {
        if (trustedProxy && forwarded != null && !forwarded.isBlank()) {
            var last = forwarded.substring(forwarded.lastIndexOf(',') + 1).trim();
            if (!last.isEmpty()) return last;
        }
        return remoteAddr;
    }
}
