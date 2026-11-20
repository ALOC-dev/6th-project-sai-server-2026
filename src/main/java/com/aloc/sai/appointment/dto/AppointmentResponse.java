package com.aloc.sai.appointment.dto;

import com.aloc.sai.appointment.Appointment;
import com.aloc.sai.appointment.AppointmentStatus;

import java.time.LocalDateTime;

public record AppointmentResponse(
        Long appointmentId,
        Long chatRoomId,
        String chatRoomName,
        AppointmentStatus status,
        LocalDateTime departAt,
        LocalDateTime returnAt,
        int memberCount,
        Integer maxMembers,
        Long recommendationRouteId,
        Long createdBy
) {
    public static AppointmentResponse of(Appointment a, String chatRoomName, int memberCount) {
        return new AppointmentResponse(
                a.getAppointmentId(), a.getChatRoom().getChatRoomId(), chatRoomName, a.getStatus(),
                a.getDepartAt(), a.getReturnAt(), memberCount, a.getMaxMembers(),
                a.getRecommendationRouteId(), a.getUser().getUserId());
    }
}
