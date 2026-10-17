package com.soma.yeolo.global.i18n;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** 언어 관련 서블릿 필터 등록. {@link ContentLanguageFilter} 설명 참고. */
@Configuration
public class I18nConfig {

    @Bean
    public FilterRegistrationBean<ContentLanguageFilter> contentLanguageFilter(SupportedLanguages languages) {
        FilterRegistrationBean<ContentLanguageFilter> registration =
                new FilterRegistrationBean<>(new ContentLanguageFilter(languages));
        // 인증 실패(401) 응답에도 붙어야 하므로 보안 필터보다 앞에 둔다.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.addUrlPatterns("/api/*");
        return registration;
    }
}
