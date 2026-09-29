package com.company.orchestrator.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 登录限速纯逻辑：阈值锁定、成功重置、窗口过期回收、阈值关闭与 IP 解析策略。
 * Limiter math: threshold lockout, success reset, window expiry with entry
 * recycling, disabled mode, and the trusted-proxy IP policy.
 */
class LoginRateLimiterTest {

    @Test
    void blocksAtThresholdAndResetClears() {
        var limiter = new LoginRateLimiter(2, 60_000L, false);
        limiter.recordFailure("u|1.1.1.1");
        assertFalse(limiter.blocked("u|1.1.1.1"));
        limiter.recordFailure("u|1.1.1.1");
        assertTrue(limiter.blocked("u|1.1.1.1"));
        limiter.reset("u|1.1.1.1");
        assertFalse(limiter.blocked("u|1.1.1.1"));
    }

    @Test
    void lockExpiresAndEntryIsRecycledAfterWindow() throws InterruptedException {
        var limiter = new LoginRateLimiter(1, 40L, false); // L 后缀选毫秒构造器（否则匹配到「分钟」构造器）/ L picks the millis ctor
        limiter.recordFailure("u|2.2.2.2");
        assertTrue(limiter.blocked("u|2.2.2.2"));
        Thread.sleep(60);
        // 过期窗口解锁且回收空条目 / expired windows unlock and recycle their entry
        assertFalse(limiter.blocked("u|2.2.2.2"));
        assertFalse(limiter.blocked("u|2.2.2.2"));
    }

    @Test
    void disabledWhenThresholdZero() {
        var limiter = new LoginRateLimiter(0, 60_000L, false);
        limiter.recordFailure("u|3.3.3.3");
        assertFalse(limiter.blocked("u|3.3.3.3"));
    }

    @Test
    void xffIsIgnoredUnlessProxyTrusted() {
        // 默认忽略可伪造的 XFF；可信代理时取末段（直连代理追加的真实来源）
        // XFF is client-controlled by default; behind a trusted proxy use the last hop
        assertEquals("9.9.9.9", new LoginRateLimiter(5, 60_000, true).pickIp("1.2.3.4, 9.9.9.9", "127.0.0.1"));
        assertEquals("127.0.0.1", new LoginRateLimiter(5, 60_000L, false).pickIp("1.2.3.4, 9.9.9.9", "127.0.0.1"));
        assertEquals("127.0.0.1", new LoginRateLimiter(5, 60_000, true).pickIp("  ", "127.0.0.1"));
    }
}
