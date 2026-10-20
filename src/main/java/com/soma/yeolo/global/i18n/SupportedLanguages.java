package com.soma.yeolo.global.i18n;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 언어 코드 검증과 응답 언어 결정 (API-USER-3/4 §언어 헤더 정책, API-LOC-1/2/3 §언어 정책).
 *
 * <ul>
 *   <li>{@link #normalize}: 요청 본문의 {@code language}가 지원 코드인지 본다. 대소문자만 무시하고
 *       ({@code KO}→{@code ko}) 그 밖의 변형({@code ko-KR})은 받지 않는다 — 지역 코드 매칭은 명세가
 *       "별도 확정"이라 BE 가 임의로 고쳐 저장하면 FE·명세와 어긋난다. 지원하지 않으면 빈 값.</li>
 *   <li>{@link #resolveMessageLanguage}: 응답 {@code message}·{@code Content-Language}의 언어.
 *       {@code Accept-Language}를 RFC 4647 조회({@link Locale#lookupTag})로 <b>렌더링 가능한</b> 언어와
 *       맞춰 보고, 없으면 기본 언어다. 미지원 언어만 왔다고 400 을 내지 않는다(명세).</li>
 *   <li>{@link #matchContentLanguage}: 응답 <b>콘텐츠</b>(국가·도시 표시명)의 언어를 헤더에서 고른다.
 *       호출자가 준 후보(데이터셋이 표시명을 가진 언어, {@code LocationLanguage.codes()})와 맞춰 보고 없으면
 *       빈 값 — 저장된 선호 언어와의 우선순위는 호출자({@code LocationLanguageResolver})가 정한다.
 *       {@code language.supported}와 묶지 않는다 — 그 목록은 사용자 저장값이자 AI 요청의 언어라서, 데이터셋
 *       표시명과는 별개의 결정이다.</li>
 * </ul>
 */
@Component
public class SupportedLanguages {

    private final List<String> supported;
    private final String fallback;
    private final List<String> messageLanguages;

    public SupportedLanguages(LanguageProperties properties) {
        this.supported = properties.supported().stream().map(SupportedLanguages::canonical).toList();
        this.fallback = canonical(properties.fallback());
        this.messageLanguages = properties.messageLanguages().stream().map(SupportedLanguages::canonical).toList();
        if (!this.supported.contains(this.fallback)) {
            throw new IllegalStateException(
                    "language.fallback(" + fallback + ")은 language.supported" + supported + " 안에 있어야 한다");
        }
        if (!this.messageLanguages.contains(this.fallback)) {
            throw new IllegalStateException(
                    "language.fallback(" + fallback + ")은 language.message-languages" + messageLanguages + " 안에 있어야 한다");
        }
    }

    public String fallback() {
        return fallback;
    }

    public List<String> supported() {
        return supported;
    }

    /** 지원 코드면 정규화(소문자)된 코드를, 아니면 빈 값을 돌려준다. */
    public Optional<String> normalize(String code) {
        if (!StringUtils.hasText(code)) {
            return Optional.empty();
        }
        String tag = canonical(code);
        return supported.contains(tag) ? Optional.of(tag) : Optional.empty();
    }

    /** 응답 메시지 언어: 헤더에서 렌더링 가능한 언어를 우선순위대로 찾고, 없으면 기본 언어. */
    public String resolveMessageLanguage(String acceptLanguage) {
        return resolve(acceptLanguage, messageLanguages);
    }

    /** 응답 콘텐츠(국가·도시 표시명) 언어: 헤더에서 {@code candidates}를 우선순위대로 찾는다. 없거나 깨진 헤더면 빈 값. */
    public Optional<String> matchContentLanguage(String acceptLanguage, List<String> candidates) {
        return match(acceptLanguage, candidates);
    }

    private String resolve(String acceptLanguage, List<String> candidates) {
        return match(acceptLanguage, candidates).orElse(fallback);
    }

    private static Optional<String> match(String acceptLanguage, List<String> candidates) {
        if (!StringUtils.hasText(acceptLanguage)) {
            return Optional.empty();
        }
        List<Locale.LanguageRange> ranges;
        try {
            ranges = Locale.LanguageRange.parse(acceptLanguage);
        } catch (IllegalArgumentException e) {
            return Optional.empty();   // 깨진 헤더는 "없음"으로 — 400 이 아니다(명세)
        }
        return Optional.ofNullable(Locale.lookupTag(ranges, candidates));
    }

    private static String canonical(String tag) {
        return tag.strip().toLowerCase(Locale.ROOT);
    }
}
