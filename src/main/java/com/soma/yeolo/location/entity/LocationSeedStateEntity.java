package com.soma.yeolo.location.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 기준 데이터셋 적재 상태. 데이터셋 파일마다 마지막으로 적재한 버전을 한 행으로 기록한다.
 *
 * <p>이게 없으면 부팅마다 국가·도시 전체를 다시 넣어야 한다. 그 자체도 낭비지만, 더 큰 문제는
 * 롤링 배포다 — 새 파드가 재적재하려고 기존 행을 지우는 동안 <b>구 파드가 같은 테이블을 서빙</b>한다.
 * 버전이 같으면 건너뛰게 해서, 재적재는 데이터셋이 실제로 바뀐 배포에서 한 번만 일어나게 한다.
 *
 * <p>이 엔티티를 코드에서 생성·조회하지는 않는다. {@code LocationSeedWriter}가 적재 트랜잭션 안에서
 * SQL로 직접 읽고 쓴다 — 여기 선언은 {@code ddl-auto}가 테이블을 만들도록 스키마를 정의하는 역할이다.
 */
@Getter
@Entity
@Table(name = "location_seed_state")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocationSeedStateEntity {

    /** 데이터셋 식별자 ({@code countries} | {@code cities}). */
    @Id
    @Column(name = "dataset", length = 32, nullable = false, updatable = false)
    private String dataset;

    /** 데이터셋 파일의 {@code #version} 헤더 값. */
    @Column(name = "version", nullable = false, columnDefinition = "text")
    private String version;
}
