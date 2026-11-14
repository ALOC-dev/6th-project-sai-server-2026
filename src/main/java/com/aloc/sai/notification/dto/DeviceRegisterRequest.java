package com.aloc.sai.notification.dto;

import com.aloc.sai.notification.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DeviceRegisterRequest(
        @NotBlank(message = "pushToken은 필수입니다.")
        @Size(max = 255, message = "pushToken은 255자 이하여야 합니다.")
        @Pattern(regexp = "^Expo(nent)?PushToken\\[.+]$", message = "Expo 푸시 토큰 형식이 아닙니다.")
        String pushToken,

        @NotNull(message = "platform은 필수입니다.")
        DevicePlatform platform
) {
}
