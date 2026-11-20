package com.aloc.sai.appointment.dto;

import java.time.LocalDateTime;

public record AppointmentUpdateRequest(
        String status,
        LocalDateTime departAt,
        LocalDateTime returnAt,
        Integer maxMembers
) {
}
