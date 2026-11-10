package com.aloc.sai.friend;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {
    Optional<FriendRequest> findByPendingKey(String pendingKey);

    // 같은 요청을 동시에 수락·거절해도 한 번만 처리되도록 행을 잠근다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select fr from FriendRequest fr where fr.friendRequestId = :friendRequestId")
    Optional<FriendRequest> findByIdForUpdate(@Param("friendRequestId") Long friendRequestId);

    @Query("""
            select fr from FriendRequest fr
            join fetch fr.requester
            where fr.receiver.userId = :userId and fr.requestStatus = :status
            order by fr.createdAt desc, fr.friendRequestId desc
            """)
    List<FriendRequest> findReceived(@Param("userId") Long userId, @Param("status") FriendRequestStatus status);

    @Query("""
            select fr from FriendRequest fr
            join fetch fr.receiver
            where fr.requester.userId = :userId and fr.requestStatus = :status
            order by fr.createdAt desc, fr.friendRequestId desc
            """)
    List<FriendRequest> findSent(@Param("userId") Long userId, @Param("status") FriendRequestStatus status);

    @Modifying
    @Query("delete from FriendRequest fr where fr.requester.userId = :userId or fr.receiver.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
