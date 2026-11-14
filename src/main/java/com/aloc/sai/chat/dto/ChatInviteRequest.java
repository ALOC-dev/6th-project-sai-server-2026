package com.aloc.sai.chat.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ChatInviteRequest(
        @NotEmpty
        List<Long> inviteeIds
) {
}
