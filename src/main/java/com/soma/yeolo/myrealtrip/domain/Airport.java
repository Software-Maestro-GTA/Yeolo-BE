package com.soma.yeolo.myrealtrip.domain;

import java.util.regex.Pattern;

/**
 * 파트너가 돌려준 공항 (API-MRT-1). 코드는 공급자 값만 쓴다 — BE 가 도시명으로 코드를 추정하지 않는다(명세 §처리 규칙).
 *
 * @param code   대문자 영문 3자리 공항 코드 (예: ICN)
 * @param koName 한글 이름. 공급자 미제공 시 null
 * @param enName 영문 이름. 공급자 미제공 시 null
 */
public record Airport(String code, String koName, String enName) {

    private static final Pattern CODE = Pattern.compile("^[A-Z]{3}$");

    /** 명세가 허용하는 코드 형식인가. 형식이 다른 항목은 결과에서 제외한다. */
    public static boolean isValidCode(String code) {
        return code != null && CODE.matcher(code).matches();
    }
}
