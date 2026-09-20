package com.company.orchestrator.common.config;

import java.io.IOException;

import com.company.orchestrator.auth.LoginRateLimiter;
import com.company.orchestrator.auth.LoginRateLimitFilter;
import com.company.orchestrator.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 同源会话认证、CSRF 保护与四角色权限。
 * Same-origin sessions with CSRF protection and organization-wide role permissions.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String KEY_LOGIN_SUCCESS = "auth.login.success";
    private static final String KEY_LOGIN_FAILURE = "auth.login.failure";
    private static final String KEY_LOGOUT_SUCCESS = "auth.logout.success";
    private static final String KEY_UNAUTHORIZED = "auth.unauthorized";
    private static final String KEY_FORBIDDEN = "auth.forbidden";

    private final MessageSource messageSource;
    private final LoginRateLimiter rateLimiter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(a -> a
            .requestMatchers("/api/v1/auth/csrf", "/api/v1/auth/login", "/actuator/health", "/", "/index.html", "/assets/**", "/favicon.svg").permitAll()
            .requestMatchers("/api/v1/auth/users/**", "/api/v1/system/**", "/actuator/**", "/v3/api-docs/**", "/swagger-ui/**").hasRole("ADMIN")
            .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/**").authenticated()
            .requestMatchers("/api/v1/auth/logout").authenticated()
            .requestMatchers("/api/v1/employees/**", "/api/v1/departments/**", "/api/v1/skills/**", "/api/v1/skill-categories/**").hasAnyRole("ADMIN", "DEPARTMENT_MANAGER")
            .requestMatchers("/api/**").hasAnyRole("ADMIN", "PROJECT_MANAGER")
            .anyRequest().denyAll())
            .formLogin(f -> f.loginProcessingUrl("/api/v1/auth/login")
                .successHandler((req, res, auth) -> { rateLimiter.reset(LoginRateLimiter.key(auth.getName(), LoginRateLimiter.clientIp(req))); respond(req, res, 200, "OK", KEY_LOGIN_SUCCESS); })
                .failureHandler((req, res, ex) -> { rateLimiter.recordFailure(LoginRateLimiter.key(req.getParameter("username"), LoginRateLimiter.clientIp(req))); respond(req, res, 401, "UNAUTHORIZED", KEY_LOGIN_FAILURE); }))
            .logout(l -> l.logoutUrl("/api/v1/auth/logout").deleteCookies("JSESSIONID")
                .logoutSuccessHandler((req, res, auth) -> respond(req, res, 200, "OK", KEY_LOGOUT_SUCCESS)))
            .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> respond(req, res, 401, "UNAUTHORIZED", KEY_UNAUTHORIZED))
                .accessDeniedHandler((req, res, ex) -> respond(req, res, 403, "FORBIDDEN", KEY_FORBIDDEN)))
            .addFilterBefore(new LoginRateLimitFilter(rateLimiter, messageSource), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** 按请求 Accept-Language 输出双语文案 / Localize handler messages by request locale. */
    private void respond(HttpServletRequest req, HttpServletResponse res, int status, String code, String messageKey)
            throws IOException {
        String message = messageSource.getMessage(messageKey, null, messageKey, req.getLocale());
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        new ObjectMapper().writeValue(res.getWriter(), Result.fail(code, message));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }
}
