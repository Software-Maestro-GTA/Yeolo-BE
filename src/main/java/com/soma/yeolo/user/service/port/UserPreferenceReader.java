package com.soma.yeolo.user.service.port;

import java.util.Optional;
import java.util.UUID;

/**
 * 사용자 선호(MBTI) 조회 포트. 코스 생성(응용 계층)이 소유하는 아웃바운드 인터페이스로,
 * MBTI 저장 세부(JPA·테이블)는 알지 못한다(DIP). MBTI는 사용자 정보(DOM-1) 필드가 아닌 별도
 * 선호 값이므로 사용자 조회와 분리해 둔다.
 *
 * <p>구현체 {@code UserPreferenceReaderImpl}이 Spring Data에 위임한다. (docs/architecture.md §1-2, §5)
 */
public interface UserPreferenceReader {

    /**
     * 사용자의 저장된 MBTI를 조회한다. 등록된 선호가 없거나 MBTI가 비어 있으면 빈 값을 반환한다.
     */
    Optional<String> findMbtiByUserId(UUID userId);
}
