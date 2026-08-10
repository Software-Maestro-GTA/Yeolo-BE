package com.soma.yeolo.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.soma.yeolo.global.config.JpaAuditingConfig;
import com.soma.yeolo.user.client.ProfileImageProperties;
import com.soma.yeolo.user.client.ProfileImageStorage;
import com.soma.yeolo.user.domain.ProfileImage;
import com.soma.yeolo.user.domain.Provider;
import com.soma.yeolo.user.dto.UserProfileUpdateRequest;
import com.soma.yeolo.user.entity.User;
import com.soma.yeolo.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 프로필 수정이 실제로 DB에 반영되는지(dirty checking → UPDATE) 확인한다.
 * 서비스 단위 테스트는 리포지토리가 목이라 영속화 여부를 증명하지 못한다.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)  // 슬라이스 테스트는 @Configuration을 안 집어 온다 — created_at 감사값 필요
// Boot가 갈아끼우는 테스트용 임베디드 H2에서는 Hibernate가 만든 enum 체크 제약(provider/status)이
// "The database has been closed"로 깨져 users insert가 전부 실패한다. 테스트 설정의 H2
// (DB_CLOSE_DELAY=-1)를 그대로 쓰면 정상 동작하므로 교체를 끈다.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserProfilePersistenceTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager em;

    private UserProfileService service() {
        ProfileImageStorage storage = new ProfileImageStorage() {
            @Override
            public String store(UUID userId, ProfileImage image) {
                return "https://cdn.test/%s.%s".formatted(userId, image.format().getExtension());
            }

            @Override
            public void deleteAll(UUID userId) {
            }
        };
        return new UserProfileService(userRepository, storage,
                new ProfileImageProperties("stub", 1_048_576L));
    }

    private MockMultipartFile pngFile() {
        byte[] png = new byte[64];
        System.arraycopy(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A},
                0, png, 0, 8);
        return new MockMultipartFile("profileImage", "me.png", "image/png", png);
    }

    @Test
    void 수정한_프로필이_DB에_반영된다() {
        User saved = userRepository.save(
                User.createOAuthUser(Provider.GOOGLE, "sub-1", "old@gmail.com", "옛이름", "http://old"));
        UUID userId = saved.getId();
        em.flush();
        em.clear();

        service().updateProfile(userId,
                new UserProfileUpdateRequest("new@gmail.com", "새이름", pngFile()));

        // 영속성 컨텍스트를 비워, 캐시가 아니라 DB에서 다시 읽는다.
        em.flush();
        em.clear();

        User reloaded = userRepository.findById(userId).orElseThrow();
        assertThat(reloaded.getEmail()).isEqualTo("new@gmail.com");
        assertThat(reloaded.getDisplayName()).isEqualTo("새이름");
        assertThat(reloaded.getProfileImageUrl()).isEqualTo("https://cdn.test/%s.png".formatted(userId));
    }

    @Test
    void 미전송_항목은_DB에서도_그대로다() {
        User saved = userRepository.save(
                User.createOAuthUser(Provider.GOOGLE, "sub-2", "keep@gmail.com", "옛이름", "http://old"));
        UUID userId = saved.getId();
        em.flush();
        em.clear();

        service().updateProfile(userId, new UserProfileUpdateRequest(null, "새이름", null));

        em.flush();
        em.clear();

        User reloaded = userRepository.findById(userId).orElseThrow();
        assertThat(reloaded.getDisplayName()).isEqualTo("새이름");
        assertThat(reloaded.getEmail()).isEqualTo("keep@gmail.com");
        assertThat(reloaded.getProfileImageUrl()).isEqualTo("http://old");
    }
}
