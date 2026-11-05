package com.soma.yeolo.myrealtrip.service;

import static com.soma.yeolo.myrealtrip.service.MyRealTripRequests.requireUser;
import static com.soma.yeolo.myrealtrip.service.MyRealTripRequests.today;

import com.soma.yeolo.myrealtrip.dto.CouponListResponse;
import com.soma.yeolo.myrealtrip.service.port.CouponRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 마이리얼트립 쿠폰 목록 (API-MRT-7). 활성이고 적용 기간 안인 쿠폰만 저장된 표시 순서대로 돌려주고, 노출할 것이 없으면
 * 빈 배열이다. 기준일은 다른 마이리얼트립 API 와 같은 Asia/Seoul({@link MyRealTripRequests#today}).
 *
 * <p>파트너 호출은 없지만 사용자별 요청 제한은 다른 MRT API 와 같은 한도를 공유한다 — 명세가 "Server 에서 사용자별 요청
 * 제한을 적용한다"고 적었고, 2026-11-05 명세 담당이 MRT-7 Error Codes 에 429 를 추가해 이 동작을 확정했다.
 */
@Service
@RequiredArgsConstructor
public class MyRealTripCouponService {

    private final CouponRepository couponRepository;
    private final MyRealTripRateLimiter rateLimiter;
    /** 시각만 쓴다 — 기준일의 시간대는 {@link MyRealTripRequests#today}가 고정한다({@code ClockConfig}). */
    private final Clock clock;

    public CouponListResponse list(UUID userId) {
        rateLimiter.acquire(requireUser(userId));
        LocalDate today = today(clock);
        return CouponListResponse.from(couponRepository.findActiveOrderByDisplayOrder().stream()
                .filter(coupon -> coupon.isAvailableOn(today))
                .toList());
    }
}
