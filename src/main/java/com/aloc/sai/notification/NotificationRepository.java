package com.aloc.sai.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 알림 창 목록. FRIEND_REQUEST 알림은 요청의 처리 상태를 함께 가져오고, 다른 타입은 null이다.
    // 각 행은 [Notification, FriendRequestStatus]. 요청 행은 수락·거절 후에도 남아 있어 조인이 깨지지 않는다.
    @Query("""
            select n, fr.requestStatus from Notification n
            left join fetch n.actor
            left join FriendRequest fr
                   on n.notificationType = :friendRequestType and fr.friendRequestId = n.referenceId
            where n.receiver.userId = :receiverId
              and (:cursor is null or n.notificationId < :cursor)
              and (:unreadOnly = false or n.isRead = false)
            order by n.notificationId desc
            """)
    List<Object[]> findPage(@Param("receiverId") Long receiverId,
                            @Param("cursor") Long cursor,
                            @Param("unreadOnly") boolean unreadOnly,
                            @Param("friendRequestType") NotificationType friendRequestType,
                            Pageable pageable);

    long countByReceiverUserIdAndIsReadFalse(Long receiverId);

    @Modifying
    @Query("delete from Notification n where n.receiver.userId = :userId or n.actor.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
