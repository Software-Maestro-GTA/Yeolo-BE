package com.soma.yeolo.global.i18n;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 모든 응답에 {@code Content-Language}와 {@code Vary: Accept-Language}를 붙인다 (API-USER-3/4 §언어 헤더 정책: "응답 헤더
 * Content-Language에는 실제 메시지에 적용한 언어 코드를 반환한다. 성공·실패 응답에 동일").
 *
 * <p>헤더는 BE 가 실제로 렌더링할 수 있는 언어({@code language.message-languages}, 지금은 {@code ko})
 * 중에서만 고른다 — 사용자가 저장할 수 있는 언어({@code language.supported})를 넓혀도 번역 리소스가
 * 없으면 메시지는 한국어이므로, 헤더가 그 언어를 주장하면 거짓이 된다. 명세의 가운데 단계(저장된 선호
 * 언어)는 요청마다 사용자 행을 읽어야 해서 두 번째 메시지 언어를 열 때 사용자 조회 포트와 함께 넣는다.
 * 응답 {@code message} 문구 번역은 명세가 "(제안)"으로 둔 상태다(docs/spec-index.md).
 *
 * <p>{@code Vary: Accept-Language}는 응답이 그 헤더에 따라 달라진다는 표시다 — 국가·도시 표시명(API-LOC-1/2/3,
 * 인증 없는 GET)이 언어별로 다르므로, 이게 없으면 브라우저·프록시 캐시가 영어 응답을 한국어 요청에 돌려줄 수
 * 있다. 메시지 언어도 같은 헤더에 달려 있어 {@code /api/*} 전체에 붙인다. 엣지(CDN)는 {@code Vary}를 무시할 수
 * 있으므로 {@code /api/*}를 캐시하지 않거나 {@code Accept-Language}를 캐시 키·오리진 전달에 넣는 것은 인프라
 * 몫이다(docs/location-dataset.md §언어).
 *
 * <p>{@code @Component}가 아니라 {@link I18nConfig}에서 등록한다. 컴포넌트 스캔에 걸리면
 * {@code @WebMvcTest} 슬라이스마다 언어 설정 빈을 함께 요구하게 된다.
 */
public class ContentLanguageFilter extends OncePerRequestFilter {

    private final SupportedLanguages languages;

    public ContentLanguageFilter(SupportedLanguages languages) {
        this.languages = languages;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String language = languages.resolveMessageLanguage(request.getHeader(HttpHeaders.ACCEPT_LANGUAGE));
        response.setHeader(HttpHeaders.CONTENT_LANGUAGE, language);
        response.addHeader(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE);
        chain.doFilter(request, response);
    }
}
