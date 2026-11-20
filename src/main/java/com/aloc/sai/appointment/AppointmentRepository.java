package com.aloc.sai.appointment;

import com.aloc.sai.chat.ChatRoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    @Query(
            """
        select ci
        from Appointment ci
        where ci.chatRoom.chatRoomId=:chatRoomId
        """
    )
    Optional<Appointment> findByChatRoom_ChatRoomId(@Param("chatRoomId") Long chatRoomId);

    List<Appointment> findAllByUser_UserId(Long userId);

    //내가 참여 중인 방 약속
    @Query("""
        select a
        from Appointment a
        join fetch a.chatRoom r
        left join fetch r.host
        where r.chatRoomId in :chatRoomIds
          and a.status in :statuses
    """)
    List<Appointment> findAllByChatRoomIdsAndStatuses(
            @Param("chatRoomIds")List<Long> chatRoomIds,
            @Param("statuses")List<AppointmentStatus> statuses
    );

    //방장이 hostIds 중 한 명인 방의 약속
    @Query("""
        select a
        from Appointment a
        join fetch a.chatRoom r
        join fetch r.host h
        where h.userId in :hostIds
          and r.type = :type
          and a.status = :status
    """)
    List<Appointment> findAllByHostIds(
            @Param("hostIds") List<Long> hostIds,
            @Param("type") ChatRoomType type,
            @Param("status") AppointmentStatus status);
}

