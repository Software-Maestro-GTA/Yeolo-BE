-- TSK-25 (#51) 사용자 MBTI 선호 입력값 — prod 배포 전 선적용 DDL
--
-- dev(ddl-auto=update)는 배포 시 Hibernate가 이 테이블을 자동 생성하므로 수동 적용이 필요 없다.
-- prod(ddl-auto=validate)는 자동 생성하지 않으므로, 이 파일을 배포 전에 직접 적용해야
-- 파드가 기동한다. (docs/architecture.md §2)
-- 접근: SSM으로 bastion 경유 (Yeolo-Infra docs/dev-environment.md 참고)
--
-- DOM-1: "MBTI는 사용자 선호 입력값으로 별도 관리되며, 사용자 정보 자체의 필드로 저장하지 않는다."
-- 사용자당 1행(user_id UNIQUE)이며, MBTI 재입력 시 이력을 남기지 않고 덮어쓴다.
CREATE TABLE user_preferences (
    id         UUID                        NOT NULL,
    user_id    UUID                        NOT NULL,
    mbti       VARCHAR(4),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_preferences_user_id UNIQUE (user_id)
);
