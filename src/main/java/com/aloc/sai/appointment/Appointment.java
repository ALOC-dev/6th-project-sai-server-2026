package com.aloc.sai.appointment;

import com.aloc.sai.chat.ChatRoom;
import com.aloc.sai.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "appointment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long appointmentId;

    @Enumerated(EnumType.STRING)
    private AppointmentStatus status;

    private LocalDateTime departAt;//약속시간(모집 중은 희망 시간, 미정 null)

    private LocalDateTime returnAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", nullable = false)
    private User user;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chatRoomId", nullable = false, unique = true)
    private ChatRoom chatRoom;

    private Long recommendationRouteId;

    private LocalDateTime createdAt;

    public static Appointment recruiting(ChatRoom room, User host, LocalDateTime departAt) {
        Appointment a = new Appointment();
        a.status = AppointmentStatus.RECRUITING;
        a.chatRoom = room;
        a.user = host;
        a.departAt = departAt;
        a.createdAt = LocalDateTime.now();
        return a;
    }
}