package com.soma.yeolo.myrealtrip.service;

import static com.soma.yeolo.myrealtrip.service.MyRealTripRequests.invalid;
import static com.soma.yeolo.myrealtrip.service.MyRealTripRequests.requireAirportCode;
import static com.soma.yeolo.myrealtrip.service.MyRealTripRequests.requireDate;
import static com.soma.yeolo.myrealtrip.service.MyRealTripRequests.requireInt;
import static com.soma.yeolo.myrealtrip.service.MyRealTripRequests.requireUser;
import static com.soma.yeolo.myrealtrip.service.MyRealTripRequests.today;

import com.soma.yeolo.myrealtrip.client.MyRealTripPartnerClient;
import com.soma.yeolo.myrealtrip.domain.FlightSearch;
import com.soma.yeolo.myrealtrip.domain.MyRealTripUrls;
import com.soma.yeolo.myrealtrip.dto.FlightLinkRequest;
import com.soma.yeolo.myrealtrip.dto.LinkResponse;
import com.soma.yeolo.myrealtrip.dto.ProductLinkRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 마이리얼트립 제휴 링크 생성 (API-MRT-5 항공 검색 / API-MRT-6 상품).
 *
 * <p>검증 → 사용자별 요청 제한(검색과 같은 한도 공유) → 파트너 호출. 링크는 상품을 눌렀을 때 하나씩 만들고 검색 결과 전체에
 * 미리 만들지 않는다(명세 §처리 규칙 — 호출 수가 곧 파트너 쿼터다). 과거 출국일 판정 기준일은 Asia/Seoul
 * ({@link MyRealTripRequests#today})이다. 입력 URL 은 파서로 검증한 뒤에도 임의 HTTP 요청 대상이 아니라 고정된 mylink API 의
 * 본문 값으로만 쓴다.
 *
 * <p>요청 제한은 BE 요청 단위다 — 항공 링크 하나가 파트너 호출 두 번(랜딩 URL → mylink)이지만 한 번으로 센다. 한도의 목적이
 * 남용 차단이고 수치가 여유값이라 호출 수로 환산하지 않았다(설정 {@code myrealtrip.rate-limit-*} 주석).
 */
@Service
@RequiredArgsConstructor
public class MyRealTripLinkService {

    private final MyRealTripPartnerClient partnerClient;
    private final MyRealTripRateLimiter rateLimiter;
    /** 시각만 쓴다 — 기준일의 시간대는 {@link MyRealTripRequests#today}가 고정한다({@code ClockConfig}). */
    private final Clock clock;


    /**
     * 왕복 항공 검색 제휴 링크. 과거 출국일·날짜 역전·동일 공항·잘못된 인원·코드 형식은 400 이고, 공급자가 노선·조건을
     * 지원하지 않으면 구분되는 문구의 400 이다. 본문이 없으면 400. 필드 해석은 여기서, 조건 사이의 불변식(동일 공항·날짜 역전·
     * 인원)은 {@link FlightSearch} 생성자 한 곳이 판정한다 — 두 곳에 같은 규칙을 두면 어긋났을 때 500 이 된다.
     */
    public LinkResponse createFlightLink(UUID userId, FlightLinkRequest request) {
        if (request == null) {
            throw invalid();
        }
        String dep = requireAirportCode(request.depAirportCd());
        String arr = requireAirportCode(request.arrAirportCd());
        LocalDate depDate = requireDate(request.depDate());
        LocalDate arrDate = requireDate(request.arrDate());
        int adult = requireInt(request.adult(), 1);
        FlightSearch search;
        try {
            search = new FlightSearch(dep, arr, depDate, arrDate, adult);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        if (search.depDate().isBefore(today(clock))) {   // "과거"는 조건 자체의 불변식이 아니라 요청 시점의 규칙
            throw invalid();
        }
        rateLimiter.acquire(requireUser(userId));
        return new LinkResponse(partnerClient.createFlightLink(search));
    }

    /** 상품 제휴 링크. {@code targetUrl}은 HTTPS 의 myrealtrip.com(서브도메인 포함)만 받고 그 밖은 400 이다. */
    public LinkResponse createProductLink(UUID userId, ProductLinkRequest request) {
        if (request == null || !MyRealTripUrls.isProductUrl(request.targetUrl())) {
            throw invalid();
        }
        rateLimiter.acquire(requireUser(userId));
        return new LinkResponse(partnerClient.createProductLink(request.targetUrl()));
    }
}
