package com.soma.yeolo.myrealtrip.dto;

/**
 * API-MRT-5 요청 본문. 전부 문자열로 받아 서비스가 해석한다 — JSON 숫자 {@code 2}는 Jackson 의 스칼라 강제 변환으로
 * {@code "2"}가 되어 {@code 1.5}·{@code 0}·누락·형식 오류가 명세의 한 400 문구("요청 값을 확인해주세요.")로 모인다.
 * 객체·배열 같은 비스칼라 값과 깨진 JSON 은 역직렬화가 실패하지만 컨트롤러의 핸들러가 같은 문구로 낸다.
 * 명세는 {@code adult}를 integer 로 적었는데 JSON 문자열 {@code "2"}도 통과한다 — 거절할 이유가 없어 그대로 둔다.
 */
public record FlightLinkRequest(String depAirportCd, String arrAirportCd, String depDate, String arrDate, String adult) {
}
