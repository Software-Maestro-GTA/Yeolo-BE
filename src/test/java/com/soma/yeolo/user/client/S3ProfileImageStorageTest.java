package com.soma.yeolo.user.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.user.domain.ImageFormat;
import com.soma.yeolo.user.domain.ProfileImage;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * S3 어댑터가 만드는 오브젝트 키·Content-Type·공개 URL을 검증한다. 실제 S3를 호출하지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class S3ProfileImageStorageTest {

    @Mock
    private S3Client s3Client;

    private final UUID userId = UUID.randomUUID();

    private S3ProfileImageStorage storage(String keyPrefix) {
        return new S3ProfileImageStorage(s3Client, new S3ProfileImageProperties(
                "yeolo-profile", "ap-northeast-2", "https://cdn.yeolo.app", keyPrefix));
    }

    private ProfileImage pngImage() {
        return new ProfileImage(new byte[]{1, 2, 3}, ImageFormat.PNG);
    }

    @Test
    void 접두사_사용자_랜덤_확장자_형태의_키로_업로드하고_공개_URL을_돌려준다() {
        String url = storage("profile-images").store(userId, pngImage());

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("yeolo-profile");
        assertThat(request.getValue().key())
                .matches("profile-images/" + userId + "/[0-9a-f-]{36}\\.png");
        assertThat(url).isEqualTo("https://cdn.yeolo.app/" + request.getValue().key());
    }

    /** 응답 헤더가 되는 값이므로 클라이언트 주장이 아니라 시그니처로 판정한 형식에서 나와야 한다. */
    @Test
    void Content_Type은_판정된_형식에서_가져온다() {
        storage("profile-images").store(userId, new ProfileImage(new byte[]{1}, ImageFormat.WEBP));

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().contentType()).isEqualTo("image/webp");
    }

    @Test
    void 접두사가_비면_키_앞에_슬래시를_붙이지_않는다() {
        storage("").store(userId, pngImage());

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().key()).startsWith(userId.toString() + "/");
    }

    /** 업로드 실패를 성공으로 응답하면 존재하지 않는 URL이 프로필에 박힌다. */
    @Test
    void 업로드_실패는_500으로_드러낸다() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("connection reset"));

        assertThatThrownBy(() -> storage("profile-images").store(userId, pngImage()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PROFILE_IMAGE_UPLOAD_FAILED);
    }

    private ListObjectsV2Response page(boolean truncated, String nextToken, String... keys) {
        return ListObjectsV2Response.builder()
                .contents(java.util.Arrays.stream(keys)
                        .map(key -> S3Object.builder().key(key).build())
                        .toList())
                .isTruncated(truncated)
                .nextContinuationToken(nextToken)
                .build();
    }

    /** 탈퇴 파기는 현재 이미지 한 장이 아니라 사용자 프리픽스 아래 전부를 지워야 한다. */
    @Test
    void 탈퇴_파기는_사용자_프리픽스의_모든_객체를_지운다() {
        String prefix = "profile-images/" + userId + "/";
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(page(false, null, prefix + "a.png", prefix + "b.webp"));

        storage("profile-images").deleteAll(userId);

        ArgumentCaptor<ListObjectsV2Request> listed = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client).listObjectsV2(listed.capture());
        assertThat(listed.getValue().prefix()).isEqualTo(prefix);

        ArgumentCaptor<DeleteObjectsRequest> deleted = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(deleted.capture());
        assertThat(deleted.getValue().delete().objects())
                .extracting(ObjectIdentifier::key)
                .containsExactly(prefix + "a.png", prefix + "b.webp");
    }

    /** 목록이 1000개를 넘으면 페이지네이션된다 — 첫 장만 지우고 끝내면 사진이 남는다. */
    @Test
    void 탈퇴_파기는_목록이_잘리면_다음_페이지까지_따라간다() {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(page(true, "next-token", "k1"))
                .thenReturn(page(false, null, "k2"));

        storage("profile-images").deleteAll(userId);

        verify(s3Client, times(2)).listObjectsV2(any(ListObjectsV2Request.class));
        verify(s3Client, times(2)).deleteObjects(any(DeleteObjectsRequest.class));
    }

    @Test
    void 지울_객체가_없으면_삭제를_호출하지_않는다() {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(page(false, null));

        storage("profile-images").deleteAll(userId);

        verify(s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
    }

    /** 파기 실패를 삼키면 "탈퇴 성공" 응답 뒤에 사진이 남는다. */
    @Test
    void 파기_실패는_500으로_드러낸다() {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenThrow(SdkClientException.create("connection reset"));

        assertThatThrownBy(() -> storage("profile-images").deleteAll(userId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PROFILE_IMAGE_DELETE_FAILED);
    }
}
