package com.soma.yeolo.user.controller;

import com.soma.yeolo.global.response.ApiResponse;
import com.soma.yeolo.user.dto.WithdrawRequest;
import com.soma.yeolo.user.service.UserWithdrawalService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserWithdrawalService userWithdrawalService;

    /** 회원탈퇴 (API-FB-12). Access Token으로 식별된 사용자의 계정을 탈퇴 처리한다. */
    @DeleteMapping("/me")
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal UUID userId,
                                      @RequestBody(required = false) WithdrawRequest request) {
        userWithdrawalService.withdraw(userId);
        return ApiResponse.success("회원탈퇴 성공", null);
    }
}
