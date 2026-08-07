package com.soma.yeolo.user.domain;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;

/**
 * 업로드된 프로필 이미지 (API-USER-1).
 *
 * <p>검증을 통과한 바이트와 판정된 형식만 담는다 — 즉 이 타입이 존재하면 이미 "허용 형식이고
 * 용량 한도 이내"임이 보장된다. 웹 계층 타입({@code MultipartFile})을 저장소 어댑터까지
 * 끌고 가지 않기 위한 경계이기도 하다.
 *
 * @param content 이미지 원본 바이트
 * @param format  시그니처로 판정한 형식
 */
public record ProfileImage(byte[] content, ImageFormat format) {

    /**
     * 업로드 바이트를 검증해 프로필 이미지로 만든다.
     *
     * <p>용량을 형식보다 먼저 본다. 한도를 넘은 파일은 어차피 거절되므로 시그니처를 볼 이유가 없고,
     * 명세도 413과 415를 구분한다.
     *
     * @throws BusinessException 용량 초과(413) 또는 지원하지 않는 형식(415)
     */
    public static ProfileImage of(byte[] content, long maxBytes) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_USER_PROFILE);
        }
        if (content.length > maxBytes) {
            throw new BusinessException(ErrorCode.PROFILE_IMAGE_TOO_LARGE);
        }
        ImageFormat format = ImageFormat.detect(content)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_PROFILE_IMAGE_TYPE));
        return new ProfileImage(content, format);
    }
}
