package com.company.orchestrator.common.config;

import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * i18n：按请求头 Accept-Language 切换 zh-CN / en-US 文案，默认简体中文。
 * Locale resolution from Accept-Language, defaulting to Simplified Chinese.
 */
@Configuration
public class I18nConfig {

    private static final Locale DEFAULT_LOCALE = Locale.SIMPLIFIED_CHINESE;

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(DEFAULT_LOCALE);
        return resolver;
    }
}
