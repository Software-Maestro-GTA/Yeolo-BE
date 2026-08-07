package com.soma.yeolo.location.domain;

/**
 * 사용자 검색어를 SQL {@code LIKE} 패턴으로 바꾼다 (API-LOC-1 / API-LOC-2).
 *
 * <p>검색어는 그대로 패턴에 넣으면 안 된다. {@code %}·{@code _}는 LIKE의 와일드카드라서, 사용자가
 * {@code keyword=%}만 입력해도 전체 행이 걸린다(limit이 막아주긴 하나 의도한 검색이 아니다).
 * 그래서 와일드카드 문자를 이스케이프한 뒤 패턴을 조립하고, 쿼리에서 {@code ESCAPE '!'}로 받는다.
 *
 * <p>이스케이프 문자로 역슬래시 대신 {@code !}를 쓴다 — 역슬래시는 JPQL 문자열·JDBC·DB 설정마다
 * 처리가 달라 이식성이 떨어진다. {@code !}는 어느 쪽에서도 특별한 의미가 없다.
 */
public final class LikePatterns {

    /** 쿼리의 {@code ESCAPE} 절과 반드시 같은 문자여야 한다. */
    public static final char ESCAPE_CHAR = '!';

    private LikePatterns() {
    }

    /** {@code kw%} — 앞부분 일치. */
    public static String prefix(String keyword) {
        return escape(keyword) + "%";
    }

    /** {@code %kw%} — 부분 일치. */
    public static String contains(String keyword) {
        return "%" + escape(keyword) + "%";
    }

    private static String escape(String keyword) {
        StringBuilder sb = new StringBuilder(keyword.length());
        for (char c : keyword.toCharArray()) {
            if (c == ESCAPE_CHAR || c == '%' || c == '_') {
                sb.append(ESCAPE_CHAR);
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
