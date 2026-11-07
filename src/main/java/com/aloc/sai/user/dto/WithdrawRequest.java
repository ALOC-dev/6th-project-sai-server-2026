package com.aloc.sai.user.dto;

import jakarta.validation.constraints.NotBlank;

public record WithdrawRequest(
        @NotBlank(message = "confirmation은 필수입니다.")
        String confirmation
) {
}
