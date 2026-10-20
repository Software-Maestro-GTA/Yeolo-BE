package com.soma.yeolo.global.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** 모든 응답의 Content-Language (API-USER-3/4 §언어 헤더 정책). */
class ContentLanguageFilterTest {

    private final ContentLanguageFilter filter = new ContentLanguageFilter(
            new SupportedLanguages(new LanguageProperties(List.of("ko", "en"), "ko", List.of("ko", "en"))));

    private String contentLanguageFor(String acceptLanguage) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me/language");
        if (acceptLanguage != null) {
            request.addHeader("Accept-Language", acceptLanguage);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getHeader("Content-Language");
    }

    @Test
    void 요청_헤더의_지원_언어를_응답_Content_Language로_돌려준다() throws Exception {
        assertThat(contentLanguageFor("en-US,en;q=0.9")).isEqualTo("en");
        assertThat(contentLanguageFor("ko")).isEqualTo("ko");
    }

    /** 표시명·메시지가 Accept-Language 에 달려 있으므로 캐시가 언어별로 저장하도록 Vary 를 붙인다. */
    @Test
    void 응답에_Vary_Accept_Language를_붙인다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/locations/countries");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getHeaders("Vary")).contains("Accept-Language");
    }

    @Test
    void 헤더가_없거나_미지원이면_기본_언어다() throws Exception {
        assertThat(contentLanguageFor(null)).isEqualTo("ko");
        assertThat(contentLanguageFor("ja")).isEqualTo("ko");
    }
}
