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

    private Integer maxMembers;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", nullable = false)
    private User user;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chatRoomId", nullable = false, unique = true)
    private ChatRoom chatRoom;

    private Long recommendationRouteId;

    private LocalDateTime createdAt;

    public static Appointment recruiting(ChatRoom room, User host,Integer maxMembers, LocalDateTime departAt) {
        Appointment a = new Appointment();
        a.status = AppointmentStatus.RECRUITING;
        a.chatRoom = room;
        a.user = host;
        a.departAt = departAt;
        a.createdAt = LocalDateTime.now();
        a.maxMembers=maxMembers;
        return a;
    }

    //모집 중 약속이 없는 방(DIRECT, PRIVATE)에서 바로 확정할 때
    public static Appointment confirmed(ChatRoom room, User by, LocalDateTime departAt, LocalDateTime returnAt) {
        Appointment a = new Appointment();
        a.status = AppointmentStatus.CONFIRMED;
        a.chatRoom = room;
        a.user = by;
        a.departAt = departAt;
        a.returnAt = returnAt;
        a.createdAt = LocalDateTime.now();
        return a;
    }

    //모집 중(또는 취소된) 약속을 확정. 확정한 사람이 약속 주인이 된다
    public void confirm(User by, LocalDateTime departAt, LocalDateTime returnAt) {
        this.status = AppointmentStatus.CONFIRMED;
        this.user = by;
        this.departAt = departAt;
        this.returnAt = returnAt;
    }

    public void reschedule(LocalDateTime departAt, LocalDateTime returnAt) {
        this.departAt = departAt;
        this.returnAt = returnAt;
    }

    public void changeMaxMembers(Integer maxMembers) {
        this.maxMembers = maxMembers;
    }

    public void cancel() {
        this.status = AppointmentStatus.CANCELED;
    }

    public boolean isRecruiting() {
        return status == AppointmentStatus.RECRUITING;
    }

    public boolean isCanceled() {
        return status == AppointmentStatus.CANCELED;
    }

    public void changeHost(User user){
        this.user=user;
    }
}