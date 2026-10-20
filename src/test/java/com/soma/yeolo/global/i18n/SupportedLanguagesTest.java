package com.soma.yeolo.global.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** API-USER-3/4 §언어 헤더 정책 — 저장 코드 검증과 응답 메시지 언어 결정 규칙. */
class SupportedLanguagesTest {

    /** 저장 가능 언어는 ko·en, 메시지를 낼 수 있는 언어는 ko 뿐인 전환기 설정. */
    private final SupportedLanguages languages = new SupportedLanguages(
            new LanguageProperties(List.of("ko", "en"), "ko", List.of("ko")));

    @Test
    void 지원_코드는_대소문자만_무시하고_받는다() {
        assertThat(languages.normalize("ko")).contains("ko");
        assertThat(languages.normalize("KO")).contains("ko");
        assertThat(languages.normalize(" en ")).contains("en");
    }

    /** 지역 코드 매칭은 명세가 "별도 확정"이라 BE 가 임의로 고쳐 저장하지 않는다 — 400 으로 거절한다. */
    @Test
    void 지역_코드가_붙은_태그와_미지원_코드와_빈_값은_빈_값이다() {
        assertThat(languages.normalize("ko-KR")).isEmpty();
        assertThat(languages.normalize("en_US")).isEmpty();
        assertThat(languages.normalize("ja")).isEmpty();
        assertThat(languages.normalize("")).isEmpty();
        assertThat(languages.normalize("   ")).isEmpty();
        assertThat(languages.normalize(null)).isEmpty();
    }

    @Test
    void 메시지_언어는_헤더에서_렌더링_가능한_언어만_고른다() {
        // en 은 저장 가능하지만 메시지는 ko 뿐 → Content-Language 가 en 을 주장하면 거짓이다.
        assertThat(languages.resolveMessageLanguage("en-US,en;q=0.9,ko;q=0.5")).isEqualTo("ko");
        assertThat(languages.resolveMessageLanguage("ko-KR,ko;q=0.9")).isEqualTo("ko");

        SupportedLanguages bilingual = new SupportedLanguages(
                new LanguageProperties(List.of("ko", "en"), "ko", List.of("ko", "en")));
        assertThat(bilingual.resolveMessageLanguage("ja,en;q=0.8,ko;q=0.5")).isEqualTo("en");
        assertThat(bilingual.resolveMessageLanguage("en-GB")).isEqualTo("en");
    }

    /**
     * 콘텐츠(국가·도시 표시명) 언어는 호출자가 준 후보(데이터셋이 가진 언어)에서 고른다 — 저장 가능 언어가
     * ko 뿐이어도 데이터셋 영문명은 낼 수 있고, 메시지 언어와도 별개다. 매칭이 없으면 빈 값(폴백은 호출자 몫).
     */
    @Test
    void 콘텐츠_언어는_호출자가_준_후보에서_고르고_없으면_빈_값이다() {
        SupportedLanguages koreanOnly = new SupportedLanguages(new LanguageProperties(List.of("ko"), "ko", List.of("ko")));
        List<String> dataset = List.of("ko", "en");
        assertThat(koreanOnly.matchContentLanguage("en-US,en;q=0.9,ko;q=0.5", dataset)).contains("en");
        assertThat(koreanOnly.resolveMessageLanguage("en-US,en;q=0.9,ko;q=0.5")).isEqualTo("ko");
        assertThat(koreanOnly.normalize("en")).isEmpty();
        assertThat(languages.matchContentLanguage("ko-KR,ko;q=0.9,en;q=0.8", dataset)).contains("ko");
        assertThat(languages.matchContentLanguage("ja", dataset)).isEmpty();
        assertThat(languages.matchContentLanguage(null, dataset)).isEmpty();
        assertThat(languages.matchContentLanguage(";;;=", dataset)).isEmpty();
    }

    /** 미지원 언어·깨진 헤더·헤더 없음은 400 이 아니라 기본 언어다(명세). */
    @Test
    void 헤더에_맞는_언어가_없으면_기본_언어다() {
        assertThat(languages.resolveMessageLanguage("ja")).isEqualTo("ko");
        assertThat(languages.resolveMessageLanguage(null)).isEqualTo("ko");
        assertThat(languages.resolveMessageLanguage("")).isEqualTo("ko");
        assertThat(languages.resolveMessageLanguage(";;;=")).isEqualTo("ko");
        assertThat(languages.resolveMessageLanguage("*")).isEqualTo("ko");
    }

    @Test
    void 기본값이_목록에_없으면_기동_시_실패한다() {
        assertThatThrownBy(() -> new SupportedLanguages(new LanguageProperties(List.of("ko"), "en", List.of("en"))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SupportedLanguages(new LanguageProperties(List.of("ko", "en"), "en", List.of("ko"))))
                .isInstanceOf(IllegalStateException.class);
    }
}
