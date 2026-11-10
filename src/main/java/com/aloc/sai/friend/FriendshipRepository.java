package com.aloc.sai.friend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
    // user1Id < user2Id 순서로 넘겨야 한다.
    Optional<Friendship> findByUser1UserIdAndUser2UserId(Long user1Id, Long user2Id);

    boolean existsByUser1UserIdAndUser2UserId(Long user1Id, Long user2Id);

    @Query("""
            select f from Friendship f
            join fetch f.user1
            join fetch f.user2
            where f.user1.userId = :userId or f.user2.userId = :userId
            """)
    List<Friendship> findAllWithUsersByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("delete from Friendship f where f.user1.userId = :userId or f.user2.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
