package com.aloc.sai.appointment;

import com.aloc.sai.chat.ChatRoom;
import com.aloc.sai.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private static AppointmentRepository appointmentRepository;
    //약속 모집 중
    @Transactional
    public Appointment createRecruiting(ChatRoom room, User host, Integer maxMember, LocalDateTime departAt){
        return appointmentRepository.save(Appointment.recruiting(room,host,maxMember,departAt));
    }

    //약속 목록(예정된 약속+친구 모집)

    //약속 변경, 취소

    //약속과 채팅방 매칭하기
    @Transactional(readOnly = true)
    public Optional<Appointment> findByChatRoomId(Long chatRoomId){
        return appointmentRepository.findByChatRoom_ChatRoomId(chatRoomId);
    }

}
