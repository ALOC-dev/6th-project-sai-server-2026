package com.aloc.sai.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 알림 창 목록(최신순). 문구를 만들 actor를 함께 가져온다.
    @Query("""
            select n from Notification n
            left join fetch n.actor
            where n.receiver.userId = :receiverId
              and (:cursor is null or n.notificationId < :cursor)
              and (:unreadOnly = false or n.isRead = false)
            order by n.notificationId desc
            """)
    List<Notification> findPage(@Param("receiverId") Long receiverId,
                                @Param("cursor") Long cursor,
                                @Param("unreadOnly") boolean unreadOnly,
                                Pageable pageable);

    long countByReceiverUserIdAndIsReadFalse(Long receiverId);

    // 보관 기간이 지난 알림 정리용. 읽음 여부와 관계없이 지운다.
    @Modifying
    @Query("delete from Notification n where n.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Query("delete from Notification n where n.receiver.userId = :userId or n.actor.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
