package com.soma.yeolo.user.entity;

import com.soma.yeolo.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 선호 입력값 (MBTI). MBTI는 사용자 정보(DOM-1)의 필드가 아니라 별도 선호 도메인으로 관리되며,
 * 코스 생성(API-COURSE-1) 시 취향 프로필과 함께 개인화 근거로 사용된다. (DOM-3 코스 생성 기준)
 *
 * <p>이 저장소는 코스 생성이 소비할 <b>읽기 값</b>만 보관한다. 등록/수정 API(API-PREF-1
 * {@code PATCH /api/users/me/preferences})는 별도 작업(TSK-25/29)에서 쓰기를 담당하며, 여기서는
 * {@code (user_id)} 유니크로 사용자당 한 행을 유지한다.
 */
@Getter
@Entity
@Table(name = "user_preferences")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPreferenceEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "mbti")
    private String mbti;
}
