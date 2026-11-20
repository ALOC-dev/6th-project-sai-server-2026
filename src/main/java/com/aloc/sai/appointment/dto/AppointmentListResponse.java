package com.aloc.sai.appointment.dto;

import com.aloc.sai.appointment.Appointment;
import com.aloc.sai.appointment.AppointmentStatus;
import com.aloc.sai.user.User;

import java.time.LocalDateTime;
import java.util.List;

public record AppointmentListResponse(
        List<AppointmentItem> upcoming,
        List<AppointmentItem> recruitingByFriends
) {
    public record AppointmentItem(
            Long appointmentId,
            Long chatRoomId,
            String chatRoomName,
            AppointmentStatus status,
            LocalDateTime departAt,
            int memberCount,
            Integer maxMembers,
            AppointmentHost host
    ){
        public static AppointmentItem of(Appointment a, String chatRoomName, int memberCount, User host){
            return new AppointmentItem(
                    a.getAppointmentId(),
                    a.getChatRoom().getChatRoomId(),
                    chatRoomName,
                    a.getStatus(),
                    a.getDepartAt(),
                    memberCount,
                    a.getMaxMembers(),
                    host == null ? null : new AppointmentHost(host.getUserId(), host.getNickname(), host.getProfileImageUrl()));
        }
    }
    public record AppointmentHost(Long userId, String nickname, String profileImageUrl) {
    }
}
