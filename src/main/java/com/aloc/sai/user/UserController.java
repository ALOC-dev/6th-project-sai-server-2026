package com.aloc.sai.user;

import com.aloc.sai.security.LoginUserId;
import com.aloc.sai.user.dto.NicknameCheckResponse;
import com.aloc.sai.user.dto.UserProfileResponse;
import com.aloc.sai.user.dto.UserUpdateRequest;
import com.aloc.sai.user.dto.WithdrawRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public UserProfileResponse getMe(@LoginUserId Long userId) {
        return userService.getProfile(userId);
    }

    @GetMapping("/nickname/check")
    public NicknameCheckResponse checkNickname(
            @LoginUserId Long userId,
            @RequestParam(required = false) String nickname
    ) {
        return userService.checkNickname(userId, nickname);
    }

    @PatchMapping("/me")
    public UserProfileResponse updateMe(
            @LoginUserId Long userId,
            @Valid @RequestBody UserUpdateRequest request
    ) {
        return userService.updateProfile(userId, request);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(
            @LoginUserId Long userId,
            @Valid @RequestBody WithdrawRequest request
    ) {
        userService.withdraw(userId, request.confirmation());
    }
}
