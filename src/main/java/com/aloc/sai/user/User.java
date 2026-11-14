package com.aloc.sai.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(nullable = false, unique = true)
    private Long kakaoId;

    @Column(nullable = false, unique = true, length = 20)
    private String nickname;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(length = 500)
    private String profileImageUrl;

    @Column(length = 50)
    private String statusMessage;

    // 가입 직후에는 비어 있고, 초기 설정에서 등록한다.
    @Column(length = 20)
    private String studentId;

    @Column(length = 50)
    private String department;

    @Column(nullable = false, unique = true, length = 8, updatable = false)
    private String friendCode;

    @Column(nullable = false)
    private boolean marketingAgreed;

    private LocalDateTime marketingAgreedAt;

    public User(Long kakaoId, String nickname, String profileImageUrl, String friendCode) {
        this.kakaoId = kakaoId;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.friendCode = friendCode;
    }

    public boolean isProfileCompleted() {
        return studentId != null && department != null;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void updateStudentId(String studentId) {
        this.studentId = studentId;
    }

    public void updateDepartment(String department) {
        this.department = department;
    }

    public void updateProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public void updateStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    // 값이 바뀔 때만 동의 일시를 갱신한다. 같은 값이면 기존 일시를 유지한다.
    public void updateMarketingAgreed(boolean marketingAgreed, LocalDateTime now) {
        if (this.marketingAgreed == marketingAgreed) {
            return;
        }
        this.marketingAgreed = marketingAgreed;
        this.marketingAgreedAt = marketingAgreed ? now : null;
    }
}
