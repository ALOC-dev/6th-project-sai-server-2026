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

// 푸시를 받을 기기. 한 사용자가 여러 기기를 가질 수 있고, 한 토큰은 한 사용자에게만 속한다.
@Entity
@Table(name = "user_devices", indexes = {
        @Index(name = "idx_user_device_user", columnList = "user_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDevice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long deviceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true)
    private String pushToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DevicePlatform platform;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 앱이 토큰을 등록할 때마다 갱신한다. 오래 갱신되지 않은 토큰을 정리하는 기준이 된다.
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public UserDevice(User user, String pushToken, DevicePlatform platform, LocalDateTime now) {
        this.user = user;
        this.pushToken = pushToken;
        this.platform = platform;
        this.updatedAt = now;
    }

    // 같은 기기에서 다른 계정으로 로그인하면 토큰의 주인이 바뀐다. 이전 사용자에게 가던 푸시는 이 기기로 가지 않는다.
    public void reregister(User user, DevicePlatform platform, LocalDateTime now) {
        this.user = user;
        this.platform = platform;
        this.updatedAt = now;
    }
}
