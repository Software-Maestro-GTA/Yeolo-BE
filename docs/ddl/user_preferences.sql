-- TSK-25 (#51) 사용자 MBTI 선호 입력값 — dev/prod 배포 전 선적용 DDL
-- dev·prod는 ddl-auto=validate이므로, 이 테이블이 없으면 파드 기동이 실패한다.
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
