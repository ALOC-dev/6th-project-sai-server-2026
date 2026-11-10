package com.aloc.sai.friend;

import com.aloc.sai.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// user1은 항상 id가 작은 쪽이다. 그래서 두 사용자 사이의 친구 관계는 방향과 관계없이 한 행만 생긴다.
@Entity
@Table(name = "friendships",
        uniqueConstraints = @UniqueConstraint(name = "uk_friendship_users", columnNames = {"user1_id", "user2_id"}),
        indexes = @Index(name = "idx_friendship_user2", columnList = "user2_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Friendship {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long friendshipId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user1_id", nullable = false)
    private User user1;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user2_id", nullable = false)
    private User user2;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Friendship(User user, User otherUser) {
        boolean userFirst = user.getUserId() < otherUser.getUserId();
        this.user1 = userFirst ? user : otherUser;
        this.user2 = userFirst ? otherUser : user;
    }

    public User getFriendOf(Long userId) {
        return user1.getUserId().equals(userId) ? user2 : user1;
    }
}
