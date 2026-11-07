package com.aloc.sai.user;

import com.aloc.sai.user.dto.UserProfileResponse;
import com.aloc.sai.user.dto.UserUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public UserProfileResponse getMe(@AuthenticationPrincipal UserDetails userDetails) {
        return userService.getProfile(Long.valueOf(userDetails.getUsername()));
    }

    @PatchMapping("/me")
    public UserProfileResponse updateMe(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UserUpdateRequest request
    ) {
        return userService.updateProfile(Long.valueOf(userDetails.getUsername()), request);
    }
}
