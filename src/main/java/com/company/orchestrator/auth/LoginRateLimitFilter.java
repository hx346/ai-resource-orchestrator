package com.company.orchestrator.auth;

import java.io.IOException;

import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import com.company.orchestrator.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 登录限速过滤器：锁定窗口内的登录请求直接 429，不进入认证流程。
 * Blocked logins get an immediate 429 without reaching authentication.
 * 此处 getParameter 解析的表单参数会被容器缓存，后续 formLogin 读取不受影响。
 */
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private final LoginRateLimiter limiter;
    private final MessageSource messages;

    public LoginRateLimitFilter(LoginRateLimiter limiter, MessageSource messages) {
        this.limiter = limiter;
        this.messages = messages;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"/api/v1/auth/login".equals(request.getServletPath()) || !"POST".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (limiter.blocked(LoginRateLimiter.key(req.getParameter("username"), LoginRateLimiter.clientIp(req)))) {
            res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            res.setContentType("application/json;charset=UTF-8");
            new ObjectMapper().writeValue(res.getWriter(), Result.fail("TOO_MANY_ATTEMPTS",
                    messages.getMessage("auth.login.locked", null, "auth.login.locked", req.getLocale())));
            return;
        }
        chain.doFilter(req, res);
    }
}
