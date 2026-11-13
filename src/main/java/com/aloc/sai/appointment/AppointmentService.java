package com.aloc.sai.appointment;

import com.aloc.sai.chat.ChatRoom;
import com.aloc.sai.user.User;
import com.aloc.sai.user.UserWithdrawEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
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

    //채팅방 방장이 바뀌면 약속 주인도 함께 바꾼다(약속이 없는 방은 무시)
    @Transactional
    public void changeHost(Long chatRoomId, User newHost){
        appointmentRepository.findByChatRoom_ChatRoomId(chatRoomId)
                .ifPresent(appointment -> appointment.changeHost(newHost));
    }

    // 탈퇴 트랜잭션 안에서 사용자 행을 지우기 전에 실행된다. DB 정리만 하고 예외를 삼키지 않는다.
    // 채팅방 정리(ChatRoomService, Order 1)가 먼저 방장·약속 주인을 넘긴 뒤에 실행되어야 한다.
    // 그래도 탈퇴자가 주인으로 남은 약속은 넘겨받을 사람이 없는 경우이므로 삭제한다.
    @EventListener
    @Order(2)
    @Transactional
    public void cleanUpOnWithdraw(UserWithdrawEvent event) {
        Long userId = event.userId();
        for (Appointment appointment : appointmentRepository.findAllByUser_UserId(userId)) {
            User roomHost = appointment.getChatRoom().getHost();
            if (roomHost != null && !roomHost.getUserId().equals(userId)) {
                appointment.changeHost(roomHost);
            } else {
                appointmentRepository.delete(appointment);
            }
        }
    }
}
