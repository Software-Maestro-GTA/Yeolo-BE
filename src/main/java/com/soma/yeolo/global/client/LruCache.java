package com.soma.yeolo.global.client;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 외부 조회 결과를 담는 작은 인메모리 LRU 캐시. (역지오코딩 provider 공통)
 *
 * <p>접근 순서 {@link LinkedHashMap}을 동기화 래핑한 것이라 단일 인스턴스 안에서만 유효하고 TTL이 없다.
 * 좌표→위치처럼 "같은 키면 같은 답"인 조회에만 쓴다. 항목 수가 {@code maxSize}를 넘으면 가장 오래
 * 안 쓴 항목부터 버린다.
 */
public final class LruCache {

    private LruCache() {
    }

    /**
     * @param maxSize      최대 항목 수(1 이상). 0으로 "캐시 끄기"를 의도해도 1로 보정하면 직전 결과가 그대로
     *                     재사용되므로 조용히 넘기지 않고 기동을 막는다.
     * @param propertyName 값의 출처 설정 키 — 기동 실패 메시지에 넣어 운영자가 바로 고칠 수 있게 한다.
     */
    public static <V> Map<String, V> newSynchronized(int maxSize, String propertyName) {
        if (maxSize < 1) {
            throw new IllegalArgumentException(propertyName + " must be at least 1, but was " + maxSize);
        }
        int capacity = maxSize;
        return Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, V> eldest) {
                return size() > capacity;
            }
        });
    }
}
