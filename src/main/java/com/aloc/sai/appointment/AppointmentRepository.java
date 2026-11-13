package com.aloc.sai.appointment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}

