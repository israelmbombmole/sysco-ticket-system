package com.sysco.web.config;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

@Configuration
@RequiredArgsConstructor
public class WebConfiguration implements WebMvcConfigurer {

    private final com.sysco.web.security.ForcePasswordChangeInterceptor forcePasswordChangeInterceptor;

    @Bean
    public LocaleResolver localeResolver() {
        // Always French UI (Thymeleaf #{…} and #locale); browser/session EN no longer overrides.
        return new FixedLocaleResolver(Locale.FRENCH);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/app/", "/app");
        registry.addRedirectViewController("/app/leave-management", "/app/agenda");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(forcePasswordChangeInterceptor);
    }
}
