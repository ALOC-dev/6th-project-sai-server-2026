package com.aloc.sai.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Modifying
    @Query("delete from Notification n where n.receiver.userId = :userId or n.actor.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
