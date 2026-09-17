package com.company.orchestrator.common.exception;

import java.util.Locale;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 405 处理：状态码 / 错误码 / Allow 头 / 405 handling: status, code, Allow header. */
class GlobalExceptionHandlerTest {

    private static Locale defaultLocale;

    @BeforeAll
    static void pinLocale() {
        defaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE); // 文案断言不受运行环境影响 / deterministic message
    }

    @AfterAll
    static void restoreLocale() { Locale.setDefault(defaultLocale); }

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(messageSource());

    private static ResourceBundleMessageSource messageSource() {
        var ms = new ResourceBundleMessageSource();
        ms.setBasename("messages");
        ms.setDefaultEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        return ms;
    }

    @Test
    void wrongVerbReturns405WithCodeAndAllowHeader() {
        var e = new HttpRequestMethodNotSupportedException("POST", java.util.List.of("GET", "HEAD"));
        var r = handler.handleMethodNotSupported(e);
        assertEquals(405, r.getStatusCode().value());
        assertEquals("E40500", r.getBody().code());
        assertTrue(r.getBody().message().contains("POST"));
        assertNotNull(r.getHeaders().getAllow());                       // 头存在 / header present
        assertTrue(r.getHeaders().getAllow().contains(org.springframework.http.HttpMethod.GET));
    }

    @Test
    void unknownSupportedMethodsStillReturns405WithoutAllow() {
        var e = new HttpRequestMethodNotSupportedException("PATCH"); // 未知支持集 / no supported set
        var r = handler.handleMethodNotSupported(e);
        assertEquals(405, r.getStatusCode().value());
        assertEquals("E40500", r.getBody().code());
        assertTrue(r.getHeaders().getAllow().isEmpty());
    }
}
