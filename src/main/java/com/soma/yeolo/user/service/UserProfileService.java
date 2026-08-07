package com.soma.yeolo.user.service;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.user.client.ProfileImageProperties;
import com.soma.yeolo.user.client.ProfileImageStorage;
import com.soma.yeolo.user.domain.ProfileImage;
import com.soma.yeolo.user.dto.UserProfileUpdateRequest;
import com.soma.yeolo.user.entity.User;
import com.soma.yeolo.user.repository.UserRepository;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 사용자 프로필 등록/수정 (API-USER-1 / DOM-1).
 *
 * <p>전송된 항목만 반영하는 부분 수정이다(미전송 = 유지). nullable 정책의 근거는
 * {@link UserProfileUpdateRequest} 문서를 참고.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final ProfileImageStorage profileImageStorage;
    private final ProfileImageProperties profileImageProperties;

    /**
     * 프로필을 수정하고 갱신된 사용자를 반환한다 (API-USER-1).
     *
     * <p>이미지 업로드를 <b>DB 접근보다 먼저</b> 한다. Hibernate는 첫 쿼리 시점에 커넥션을 얻으므로
     * (지연 획득), 이 순서라면 수 초가 걸릴 수 있는 S3 업로드 동안 커넥션 풀을 붙잡지 않는다.
     * 순서를 뒤집으면 업로드가 느려질수록 풀이 마른다.
     *
     * <p>업로드 성공 후 DB 갱신이 실패하면 저장소에 참조되지 않는 객체가 남는다(2PC가 없는 한
     * 불가피). 교체된 옛 이미지와 마찬가지로 버킷 수명주기 규칙으로 정리한다.
     */
    @Transactional
    public User updateProfile(UUID userId, UserProfileUpdateRequest request) {
        String profileImageUrl = uploadIfPresent(userId, request.profileImage());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String email = normalize(request.email());
        if (email != null && userRepository.existsByEmailAndDeletedAtIsNullAndIdNot(email, userId)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_IN_USE);
        }

        user.updateProfile(email, normalize(request.displayName()), profileImageUrl);
        return user;
    }

    /** 파일이 없으면 null(=이미지 유지). 있으면 검증 후 저장하고 공개 URL을 돌려준다. */
    private String uploadIfPresent(UUID userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        return profileImageStorage.store(userId, toProfileImage(file));
    }

    /** 업로드 바이트를 읽어 형식(415)·용량(413)을 검증한다. */
    private ProfileImage toProfileImage(MultipartFile file) {
        // getSize()로 먼저 걸러 한도를 크게 넘는 파일을 힙에 올리지 않는다. 서블릿 컨테이너의
        // multipart 한도(spring.servlet.multipart.max-file-size)가 1차 방어선이고, 이건 앱의 정책 한도다.
        if (file.getSize() > profileImageProperties.maxBytes()) {
            throw new BusinessException(ErrorCode.PROFILE_IMAGE_TOO_LARGE);
        }
        try {
            return ProfileImage.of(file.getBytes(), profileImageProperties.maxBytes());
        } catch (IOException e) {
            log.error("프로필 이미지 파일을 읽지 못했다.", e);
            throw new BusinessException(ErrorCode.PROFILE_IMAGE_UPLOAD_FAILED, e);
        }
    }

    /** 공백뿐인 값은 미전송과 같이 취급한다(폼이 빈 파트를 보내는 경우). 그 외에는 앞뒤 공백만 제거. */
    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }
}
