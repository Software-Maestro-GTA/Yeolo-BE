package com.soma.yeolo.location.domain;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * 자동완성 검색용 키 생성·정규화 (API-LOC-1 / API-LOC-2).
 *
 * <p>표시용 이름({@code nameKo})과 검색용 키를 분리한다. 검색을 표시용 이름에 직접 걸면 공백·구분자
 * 차이("뉴욕" vs "뉴 욕", "New York" vs "newyork")마다 후보가 사라지고, 초성 검색은 아예 불가능하다.
 * 그래서 적재 시점에 두 개의 키를 미리 계산해 컬럼으로 들고 있는다(precompute + LIKE).
 *
 * <ul>
 *   <li>{@link #normalize(String)} — 소문자화 + 공백·구분자 제거. 부분 일치 검색의 대상.
 *   <li>{@link #chosung(String)} — 한글 음절에서 초성만 뽑은 문자열. 초성 검색({@code keyword=ㄷ})의 대상.
 * </ul>
 *
 * <p>두 키는 <b>적재 시점과 조회 시점에 같은 함수로</b> 만들어야 한다 — 그래야 저장된 키와 검색어가
 * 같은 규칙 위에서 비교된다. 규칙을 바꾸면 데이터셋을 다시 적재해야 한다.
 */
public final class SearchKeys {

    /** 결합용 발음 부호(유니코드 범주 Mn). NFD 분해 후 이것만 지우면 기저 문자가 남는다. */
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{Mn}+");

    /** 한글 음절 영역 U+AC00(가) ~ U+D7A3(힣). */
    private static final char HANGUL_SYLLABLE_FIRST = '가';
    private static final char HANGUL_SYLLABLE_LAST = '힣';

    /** 한 초성이 담당하는 음절 수 = 중성(21) × 종성(28). */
    private static final int SYLLABLES_PER_CHOSUNG = 21 * 28;

    /** 초성 19자 (유니코드 호환 자모 ㄱ~ㅎ). 인덱스는 음절 분해 결과와 일치한다. */
    private static final char[] CHOSUNG = {
            'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
            'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    };

    /** 호환 자모 자음 영역 U+3131(ㄱ) ~ U+314E(ㅎ). 사용자가 초성만 입력했을 때 들어오는 문자들. */
    private static final char COMPAT_JAMO_FIRST = 'ㄱ';
    private static final char COMPAT_JAMO_LAST = 'ㅎ';

    private SearchKeys() {
    }

    /**
     * 부분 일치 검색용 키. 발음 부호를 벗기고, 소문자로 낮추고, 공백·구분자를 제거한다.
     *
     * <p>구분자를 지우므로 "new york"으로 "New York"을, "뉴 욕"으로 "뉴욕"을 찾을 수 있다.
     * GeoNames에 한국어 이름이 없는 도시는 이름이 라틴 문자로 남으므로, 이 키가 영문 검색까지
     * 함께 감당한다(응답 스키마는 명세대로 {@code nameKo} 하나만 유지).
     *
     * <p><b>발음 부호를 벗기는 이유:</b> 데이터셋의 라틴 도시명 상당수가 {@code León}·{@code Lüliang}처럼
     * 발음 부호를 달고 있다. 사용자는 그걸 입력할 수단이 없으므로({@code Leon}·{@code Luliang}으로 친다)
     * 벗겨 두지 않으면 존재하는 도시가 검색되지 않는다.
     *
     * @return 정규화된 키. 입력이 {@code null}이면 빈 문자열
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String folded = foldDiacritics(raw);
        StringBuilder sb = new StringBuilder(folded.length());
        for (char c : folded.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    /**
     * 발음 부호를 제거하고 한글 음절은 결합형으로 되돌린다.
     *
     * <p>NFD로 분해하면 발음 부호가 결합 문자(범주 {@code Mn})로 떨어져 나오므로 그것만 지운다.
     * 한글 자모는 {@code Mn}이 아니라 그대로 남고, 이어서 NFC로 되돌리면 다시 한 음절로 합쳐진다 —
     * 이 왕복이 <b>macOS에서 복사해 온 분해형(NFD) 한글</b>도 함께 정리해 준다. 분해형을 그대로 두면
     * "서울"이 여섯 글자로 보여 음절 기반 처리가 전부 어긋난다.
     */
    private static String foldDiacritics(String raw) {
        String decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD);
        String stripped = COMBINING_MARKS.matcher(decomposed).replaceAll("");
        return Normalizer.normalize(stripped, Normalizer.Form.NFC);
    }

    /**
     * 초성 검색용 키. 한글 음절은 초성으로 분해하고, 이미 초성으로 입력된 자모는 그대로 둔다.
     * 한글이 아닌 문자는 버린다.
     *
     * <p>예: {@code "제주"} → {@code "ㅈㅈ"}, {@code "뉴욕"} → {@code "ㄴㅇ"}, {@code "Tokyo"} → {@code ""}.
     *
     * @return 초성 문자열. 한글이 하나도 없으면 빈 문자열
     */
    public static String chosung(String raw) {
        if (raw == null) {
            return "";
        }
        // 분해형(NFD) 한글은 음절 영역에 들어오지 않아 초성이 하나도 잡히지 않는다 — 먼저 결합형으로.
        String folded = foldDiacritics(raw);
        StringBuilder sb = new StringBuilder(folded.length());
        for (char c : folded.toCharArray()) {
            if (c >= HANGUL_SYLLABLE_FIRST && c <= HANGUL_SYLLABLE_LAST) {
                sb.append(CHOSUNG[(c - HANGUL_SYLLABLE_FIRST) / SYLLABLES_PER_CHOSUNG]);
            } else if (isCompatJamoConsonant(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 검색어가 초성만으로 이뤄졌는지 판정한다. 참이면 {@code searchChosung} 컬럼을,
     * 거짓이면 {@code searchName} 컬럼을 대상으로 조회한다.
     *
     * <p>"ㄷ"·"ㅅㅇ"처럼 자음만 있는 입력이 초성 검색이다. "ㅅ울"처럼 초성과 음절이 섞인 입력은
     * 초성 검색으로 보지 않는다 — 그런 키는 저장해 두지 않았으므로 이름 검색으로 흘려보내고
     * (대개 결과 없음), 빈 배열로 응답한다.
     */
    public static boolean isChosungOnly(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return false;
        }
        for (char c : keyword.toCharArray()) {
            if (!isCompatJamoConsonant(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 호환 자모 자음(U+3131~U+314E) 여부. 이 영역에는 "ㄳ"·"ㄺ" 같은 겹받침 자모도 있어 실제
     * 초성 19자보다 넓지만, 그런 입력은 초성 키에 존재하지 않아 결과 없음으로 끝난다 —
     * 판정을 좁히려고 예외 목록을 들고 있을 이유가 없다.
     */
    private static boolean isCompatJamoConsonant(char c) {
        return c >= COMPAT_JAMO_FIRST && c <= COMPAT_JAMO_LAST;
    }
}
