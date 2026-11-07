package com.aloc.sai.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 보낸 필드만 수정한다. null이면 그 필드는 건드리지 않는다.
public record UserUpdateRequest(
        String nickname,

        @Pattern(regexp = "^[0-9]+$", message = "학번은 숫자만 입력할 수 있습니다.")
        @Size(max = 20, message = "학번은 20자 이하여야 합니다.")
        String studentId,

        @Pattern(regexp = "(?s).*\\S.*", message = "학과를 입력해 주세요.")
        @Size(max = 50, message = "학과는 50자 이하여야 합니다.")
        String department,

        String statusMessage,

        Boolean marketingAgreed
) {
    public boolean isEmpty() {
        return nickname == null && studentId == null && department == null
                && statusMessage == null && marketingAgreed == null;
    }
}
