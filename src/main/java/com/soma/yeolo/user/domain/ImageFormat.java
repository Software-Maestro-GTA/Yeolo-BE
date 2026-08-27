package com.soma.yeolo.user.domain;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 프로필 이미지로 허용하는 형식 (API-USER-1 §"415: 지원하지 않는 이미지 형식").
 *
 * <p>형식 판정은 요청의 {@code Content-Type}이 아니라 <b>파일 앞부분의 시그니처(매직 바이트)</b>로
 * 한다. Content-Type은 클라이언트가 정하는 값이라 {@code image/png}라고 적힌 실행 파일도 그대로
 * 통과하며, 그것이 저장소에 올라가면 우리가 서빙하는 URL로 임의 콘텐츠가 배포된다.
 * 응답 헤더용 Content-Type도 여기서 판정한 형식으로 다시 만들어 붙인다.
 */
@RequiredArgsConstructor
public enum ImageFormat {

    JPEG("image/jpeg", "jpg", new int[]{0xFF, 0xD8, 0xFF}),
    PNG("image/png", "png", new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}),
    /** WebP: {@code RIFF....WEBP} — 4~7바이트(파일 크기)를 건너뛰고 8~11바이트를 확인한다. */
    WEBP("image/webp", "webp", new int[]{0x52, 0x49, 0x46, 0x46});

    @Getter
    private final String contentType;
    @Getter
    private final String extension;
    /** getter를 두지 않는다 — 배열은 노출하면 호출부가 상수를 고칠 수 있다. */
    private final int[] signature;

    /** 바이트 시그니처로 형식을 판정한다. 허용 목록에 없으면 빈 값 — 호출부가 415로 거절한다. */
    public static Optional<ImageFormat> detect(byte[] content) {
        if (content == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(format -> format.matches(content))
                .findFirst();
    }

    private boolean matches(byte[] content) {
        if (content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((content[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        // RIFF 컨테이너는 WebP 외에 WAV·AVI 등도 쓰므로 8~11바이트의 포맷 태그까지 봐야 한다.
        return this != WEBP || isWebpRiff(content);
    }

    private boolean isWebpRiff(byte[] content) {
        return content.length >= 12
                && (content[8] & 0xFF) == 0x57   // W
                && (content[9] & 0xFF) == 0x45   // E
                && (content[10] & 0xFF) == 0x42  // B
                && (content[11] & 0xFF) == 0x50; // P
    }
}
