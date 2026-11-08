package com.aloc.sai.dto;

import com.aloc.sai.entity.*;
import com.aloc.sai.user.User;

import java.time.LocalDateTime;
import java.util.List;

public record ChatRoomResponse(
        Long chatRoomId,
        String chatRoomName,
        ChatRoomType type,
        ChatRoomStatus status,
        Long hostId,
        int memberCount,
        List<MemberResponse> members,
        AppointmentSummary appointment,
        LocalDateTime createdAt,
        LocalDateTime expiresAt
) {
    public record MemberResponse(
            Long userId,
            String nickname,
            String profileImageUrl,
            boolean isHost
    ){

    }

    public record AppointmentSummary(Long appointmentId, AppointmentStatus stauts,LocalDateTime departAt, Integer maxMembers){

    }

    public static ChatRoomResponse of(ChatRoom room, List<User> users, Appointment appointment) {
        Long hostId = room.getHost() == null ? null : room.getHost().getUserId();

        List<MemberResponse> members = users.stream()
                .map(u -> new MemberResponse(u.getUserId(), u.getNickname(), u.getProfileImageUrl(),
                        u.getUserId().equals(hostId)))
                .toList();

        AppointmentSummary summary = appointment == null ? null
                : new AppointmentSummary(appointment.getAppointmentId(), appointment.getStatus(),
                appointment.getDepartAt(), room.getMaxMembers());

        return new ChatRoomResponse(room.getChatRoomId(), room.getChatRoomName(), room.getType(),
                room.getStatus(), hostId, members.size(), members, summary,
                room.getCreatedAt(), room.getExpiresAt());
    }
}
