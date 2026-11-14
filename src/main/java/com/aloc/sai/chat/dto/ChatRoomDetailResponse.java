package com.aloc.sai.chat.dto;

import com.aloc.sai.appointment.Appointment;
import com.aloc.sai.appointment.AppointmentStatus;
import com.aloc.sai.chat.ChatRoom;
import com.aloc.sai.chat.ChatRoomStatus;
import com.aloc.sai.chat.ChatRoomType;
import com.aloc.sai.chat.MessageType;
import com.aloc.sai.user.User;

import java.time.LocalDateTime;
import java.util.List;

public record ChatRoomDetailResponse(
        Long chatRoomId,
        String chatRoomName,        // DIRECT면 null
        ChatRoomType type,          // DIRECT / PUBLIC / PRIVATE
        ChatRoomStatus status,
        Long hostId,
        Integer memberCount,
        List<Member> members,
        AppointmentSummary appointment,
        LocalDateTime createdAt,
        LocalDateTime expiresAt
        ) {
    public record Member(
            Long userId,
            String nickname,
            String profileImageUrl,
            boolean isHost
    ){}

    public record AppointmentSummary(
            Long appointmentId,
            AppointmentStatus status,
            LocalDateTime departAt,
            LocalDateTime returnAt,
            Integer maxMembers,
            Long recommendationRouteId
    ){
        public static AppointmentSummary from(Appointment appointment){
            return new AppointmentSummary(
                    appointment.getAppointmentId(),
                    appointment.getStatus(),
                    appointment.getDepartAt(),
                    appointment.getReturnAt(),
                    appointment.getMaxMembers(),
                    appointment.getRecommendationRouteId()
            );
        }
    }
}
