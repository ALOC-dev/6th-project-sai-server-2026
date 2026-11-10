package com.aloc.sai.friend;

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

// 처리한 요청도 알림의 수락/거절 로그에 쓰이므로 지우지 않고 상태만 바꾼다.
@Entity
@Table(name = "friend_requests", indexes = {
        @Index(name = "idx_friend_request_requester_status", columnList = "requester_id, requestStatus"),
        @Index(name = "idx_friend_request_receiver_status", columnList = "receiver_id, requestStatus")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FriendRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long friendRequestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FriendRequestStatus requestStatus;

    // 두 사용자 사이의 관계가 살아 있는 동안(대기 중이거나, 수락되어 친구인 동안) "작은id:큰id" 값을 갖는다.
    // 거절되거나 친구가 삭제되면 null로 비운다. UNIQUE 컬럼은 NULL을 여러 개 허용하므로
    // 한 쌍에는 방향과 관계없이 "대기 중인 요청 또는 친구 관계"가 하나만 존재한다.
    // 수락해도 값을 유지하기 때문에, 수락과 동시에 들어온 재요청도 이 제약에 걸린다.
    @Column(unique = true, length = 41)
    private String activePairKey;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime respondedAt;

    public FriendRequest(User requester, User receiver) {
        this.requester = requester;
        this.receiver = receiver;
        this.requestStatus = FriendRequestStatus.PENDING;
        this.activePairKey = pairKey(requester.getUserId(), receiver.getUserId());
    }

    public static String pairKey(Long userId, Long otherUserId) {
        return Math.min(userId, otherUserId) + ":" + Math.max(userId, otherUserId);
    }

    public boolean isPending() {
        return requestStatus == FriendRequestStatus.PENDING;
    }

    public boolean isSentBy(Long userId) {
        return requester.getUserId().equals(userId);
    }

    public boolean isReceivedBy(Long userId) {
        return receiver.getUserId().equals(userId);
    }

    // 친구 관계가 유지되는 동안 activePairKey를 그대로 둔다.
    public void accept(LocalDateTime now) {
        this.requestStatus = FriendRequestStatus.ACCEPTED;
        this.respondedAt = now;
    }

    public void reject(LocalDateTime now) {
        this.requestStatus = FriendRequestStatus.REJECTED;
        this.respondedAt = now;
        this.activePairKey = null;
    }

    // 친구가 삭제되면 같은 쌍이 다시 요청할 수 있도록 키를 비운다.
    public void endFriendship() {
        this.activePairKey = null;
    }
}
