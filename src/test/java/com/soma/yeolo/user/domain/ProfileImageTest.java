package com.soma.yeolo.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * 순수 도메인 테스트 — 프로필 이미지 검증 규칙(API-USER-1의 413/415).
 */
class ProfileImageTest {

    private static final long MAX_BYTES = 1_024;

    /** 시그니처 + 패딩으로 원하는 길이의 이미지 바이트를 만든다. */
    private static byte[] bytesOf(int[] signature, int totalLength) {
        byte[] content = new byte[totalLength];
        for (int i = 0; i < signature.length; i++) {
            content[i] = (byte) signature[i];
        }
        return content;
    }

    private static byte[] jpeg(int totalLength) {
        return bytesOf(new int[]{0xFF, 0xD8, 0xFF}, totalLength);
    }

    private static byte[] png(int totalLength) {
        return bytesOf(new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}, totalLength);
    }

    private static byte[] webp(int totalLength) {
        // RIFF....WEBP — 4~7바이트는 파일 크기라 아무 값이나 올 수 있다.
        byte[] content = bytesOf(new int[]{0x52, 0x49, 0x46, 0x46}, totalLength);
        byte[] tag = {'W', 'E', 'B', 'P'};
        System.arraycopy(tag, 0, content, 8, tag.length);
        return content;
    }

    @Test
    void JPEG_PNG_WebP_시그니처를_형식으로_판정한다() {
        assertThat(ProfileImage.of(jpeg(64), MAX_BYTES).format()).isEqualTo(ImageFormat.JPEG);
        assertThat(ProfileImage.of(png(64), MAX_BYTES).format()).isEqualTo(ImageFormat.PNG);
        assertThat(ProfileImage.of(webp(64), MAX_BYTES).format()).isEqualTo(ImageFormat.WEBP);
    }

    /** Content-Type이 아니라 실제 바이트로 판정하므로, 확장자를 속인 파일은 415로 걸린다. */
    @Test
    void 이미지가_아닌_바이트는_415다() {
        byte[] notAnImage = "GIF89a not really an image".getBytes();

        assertThatThrownBy(() -> ProfileImage.of(notAnImage, MAX_BYTES))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNSUPPORTED_PROFILE_IMAGE_TYPE);
    }

    /** RIFF 컨테이너는 WAV·AVI도 쓰므로 WEBP 태그가 없으면 이미지가 아니다. */
    @Test
    void WEBP_태그가_없는_RIFF는_415다() {
        byte[] wav = bytesOf(new int[]{0x52, 0x49, 0x46, 0x46}, 64);
        byte[] tag = {'W', 'A', 'V', 'E'};
        System.arraycopy(tag, 0, wav, 8, tag.length);

        assertThatThrownBy(() -> ProfileImage.of(wav, MAX_BYTES))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNSUPPORTED_PROFILE_IMAGE_TYPE);
    }

    @Test
    void 시그니처보다_짧은_파일은_415다() {
        assertThatThrownBy(() -> ProfileImage.of(new byte[]{(byte) 0xFF}, MAX_BYTES))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNSUPPORTED_PROFILE_IMAGE_TYPE);
    }

    @Test
    void 한도를_넘으면_413이고_형식보다_먼저_판정한다() {
        // 형식조차 알 수 없는 바이트인데도 415가 아닌 413이어야 한다(용량을 먼저 본다).
        byte[] tooLarge = new byte[(int) MAX_BYTES + 1];
        Arrays.fill(tooLarge, (byte) 0x01);

        assertThatThrownBy(() -> ProfileImage.of(tooLarge, MAX_BYTES))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PROFILE_IMAGE_TOO_LARGE);
    }

    @Test
    void 한도와_같은_크기는_통과한다() {
        assertThat(ProfileImage.of(jpeg((int) MAX_BYTES), MAX_BYTES).content())
                .hasSize((int) MAX_BYTES);
    }

    @Test
    void 빈_파일은_400이다() {
        assertThatThrownBy(() -> ProfileImage.of(new byte[0], MAX_BYTES))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_USER_PROFILE);
    }

    /** 저장소에 실어 보낼 Content-Type은 클라이언트 주장이 아니라 판정 결과에서 나온다. */
    @Test
    void 판정된_형식이_Content_Type과_확장자를_결정한다() {
        assertThat(ImageFormat.PNG.getContentType()).isEqualTo("image/png");
        assertThat(ImageFormat.PNG.getExtension()).isEqualTo("png");
        assertThat(ImageFormat.JPEG.getContentType()).isEqualTo("image/jpeg");
        assertThat(ImageFormat.JPEG.getExtension()).isEqualTo("jpg");
    }
}
