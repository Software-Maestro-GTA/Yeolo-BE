package com.soma.yeolo.share.entity;

import com.soma.yeolo.global.entity.BaseTimeEntity;
import com.soma.yeolo.share.domain.CourseAccess;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공유 수락으로 부여된 코스 접근 권한 (DOM-6). 테이블 스펙이 명세에 없어 BE에서 정한 스키마다
 * (docs/ddl/course_accesses.sql).
 *
 * <p>{@code (course_id, user_id)} 유니크 제약이 "같은 링크를 여러 번 수락해도 중복으로 코스가
 * 추가되지 않는다"(DOM-6 §보안 및 운영 정책)의 최종 방어선이다 — 서비스의 사전 검사가 동시
 * 요청에서 뚫리더라도 DB가 막는다.
 */
@Getter
@Entity
@Table(name = "course_accesses",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_course_accesses_course_id_user_id",
                        columnNames = {"course_id", "user_id"})
        },
        indexes = {
                @Index(name = "idx_course_accesses_user_id", columnList = "user_id")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseAccessEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** 어떤 초대 링크로 들어왔는지. 링크 행이 지워져도 권한은 남으므로 FK가 아닌 값으로 둔다. */
    @Column(name = "share_link_id")
    private UUID shareLinkId;

    private CourseAccessEntity(UUID courseId, UUID userId, UUID shareLinkId) {
        this.courseId = courseId;
        this.userId = userId;
        this.shareLinkId = shareLinkId;
    }

    /** 순수 도메인 → 영속 엔티티 매핑. */
    public static CourseAccessEntity from(CourseAccess access) {
        return new CourseAccessEntity(access.courseId(), access.userId(), access.shareLinkId());
    }
}
