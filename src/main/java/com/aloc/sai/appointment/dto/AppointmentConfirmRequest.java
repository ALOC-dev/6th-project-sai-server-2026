package com.aloc.sai.appointment.dto;

import java.time.LocalDateTime;

public record AppointmentConfirmRequest(
        LocalDateTime departAt,
        LocalDateTime returnAt,
        Long recommendationRouteId//기능구현해야함
) {
}
