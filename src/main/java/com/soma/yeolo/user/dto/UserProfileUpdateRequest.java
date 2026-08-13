package com.soma.yeolo.user.dto;

import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

/**
 * 사용자 프로필 등록/수정 요청 (API-USER-1). {@code multipart/form-data}로 받는다.
 *
 * <p><b>명세 개정으로 {@code email}이 요청에서 빠졌다.</b> 이제 이메일은 OAuth 제공자가 준 값만
 * 쓰며 사용자가 고칠 수 없다. 응답에는 여전히 실려 나간다(조회 전용).
 *
 * <p><b>nullable 정책(DOM-1):</b> 두 항목 모두 선택 입력이며, <b>보내지 않은 항목은 변경하지
 * 않는다.</b> 명세상 둘 다 nullable이라 요청 본문만으로 "안 보냄"과 "null로 지움"을 구분할 수
 * 없는데, 안 보낸 항목을 null로 덮으면 이름만 고쳐도 프로필 이미지가 지워진다. 값을 지우는 수단은
 * 이 API가 제공하지 않는다(DOM-1의 null은 OAuth가 값을 안 준 경우를 위한 것이지, 사용자가 지우기
 * 위한 것이 아니다).
 *
 * <p>{@code displayName} 길이 상한은 명세에 없어 서버가 정한다. 상한이 없으면 컬럼 길이(255)를
 * 넘는 값이 DB 오류(500)로 드러나므로, 표시용으로 무리 없는 50자에서 400으로 끊는다.
 *
 * @param displayName  변경할 표시 이름 (미전송 시 유지)
 * @param profileImage 새 프로필 이미지 파일 (미전송 시 유지)
 */
public record UserProfileUpdateRequest(

        @Size(max = 50, message = "사용자 프로필 입력값을 확인해주세요.")
        String displayName,

        MultipartFile profileImage
) {
}
