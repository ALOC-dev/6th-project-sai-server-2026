package com.aloc.sai.notification;

import com.aloc.sai.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// 알림 창에 보여 줄 기록. 푸시 발송 여부와 관계없이 남는다.
@Entity
@Table(name = "notifications", indexes = {
        // 알림 목록(최신순)과 안 읽은 알림 수 조회용
        @Index(name = "idx_notification_receiver_read_created", columnList = "receiver_id, isRead, createdAt")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    // 알림을 발생시킨 사용자. 약속 리마인더 같은 시스템 알림은 발생시킨 사람이 없어 null이다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationType notificationType;

    // 타입마다 가리키는 대상이 달라 FK를 걸지 않는다(NotificationType 참고).
    private Long referenceId;

    @Column(nullable = false)
    private boolean isRead;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Notification(User receiver, User actor, NotificationType notificationType, Long referenceId) {
        this.receiver = receiver;
        this.actor = actor;
        this.notificationType = notificationType;
        this.referenceId = referenceId;
    }
}
