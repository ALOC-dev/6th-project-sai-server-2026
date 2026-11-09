package com.aloc.sai.chat;

import com.aloc.sai.chat.dto.CreateChatRoomResult;
import com.aloc.sai.common.exception.ApiException;
import com.aloc.sai.chat.dto.ChatRoomResponse;
import com.aloc.sai.chat.dto.CreateChatRoomRequest;
import com.aloc.sai.appointment.Appointment;
import com.aloc.sai.appointment.AppointmentRepository;
import com.aloc.sai.user.User;
import com.aloc.sai.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private static final int NAME_MAX_LENGTH = 20;
    private static final int MIN_MEMBERS = 2;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatInvitedRepository chatInvitedRepository;
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;

    //채팅방 생성
    @Transactional
    public CreateChatRoomResult chatRoomcreate(Long userId, CreateChatRoomRequest req) {
        ChatRoomType type=req.type();
        if(type==null){
            throw badRequest("type은 필수입니다.");
        }

        //초대 목록(userId제외)
        List<Long> inviteeIds=req.inviteeIds()==null?List.of()
                :req.inviteeIds().stream()
                .filter(id->id!=null&&!id.equals(userId))
                .distinct()
                .toList();

        //입력 검증
        if(type==ChatRoomType.DIRECT && inviteeIds.size()!=1){
            throw badRequest("DIRECT는 상대 1명을 초대해야 합니다.");
        }
        if(type!=ChatRoomType.DIRECT){
            String name=req.chatRoomName();
            if(name==null || name.isBlank() || name.strip().length()>20){
                throw badRequest("채팅방 이름은 1~20자여야 합니다.");
            }
        }
        if(type==ChatRoomType.PUBLIC){
            if(req.departAt()!=null && req.departAt().isBefore(LocalDateTime.now())){
                throw badRequest("약속 시간은 현재 이후여야 합니다.");
            }
            Integer max=req.maxMembers();
            if (max != null && (max < 2 || 1 + inviteeIds.size() > max))
                throw badRequest("정원은 2명 이상이고 초대 인원(본인 포함)보다 커야 합니다.");
        }

        //TODO:친구확인
        User me = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        List<User> invitees = userRepository.findAllById(inviteeIds);
        if(invitees.size()!=inviteeIds.size()){
            throw new ApiException(HttpStatus.NOT_FOUND, "초대할 사용자를 찾을 수 없습니다.");
        }

        //이미 존재했던 1:1 방을 반환(중복 1:1 채팅방 생기기 막기)
        if(type==ChatRoomType.DIRECT){
            List<Long> ids=chatRoomRepository.findDirectRoomIds(
                    userId,inviteeIds.get(0),ChatRoomType.DIRECT, ChatRoomStatus.ACTIVE,
                    LocalDateTime.now().minusHours(24)
                    );
            if(!ids.isEmpty()){
                ChatRoom existing = chatRoomRepository.findById(ids.get(0)).orElseThrow();
                List<User> existingMembers=chatInvitedRepository.findMembers(existing.getChatRoomId());
                return new CreateChatRoomResult(ChatRoomResponse.of(existing, existingMembers, null), false);
            }
        }

        ChatRoom room=chatRoomRepository.save(
                ChatRoom.create(type, req.chatRoomName(), me, req.maxMembers())
        );
        List<User> members=new ArrayList<>();
        members.add(me);
        members.addAll(invitees);
        chatInvitedRepository.saveAll(members.stream().map(u -> ChatInvite.of(u, room)).toList());

        //public시 모집 중 약속 있어야함
        Appointment appointment=(type==ChatRoomType.PUBLIC)?
                appointmentRepository.save(Appointment.recruiting(room,me,req.departAt()))
                :null;

        //TODO:초대 알림구현

        return new CreateChatRoomResult(ChatRoomResponse.of(room,members,appointment),true);
    }

    private ApiException badRequest(String message){
        return new ApiException(HttpStatus.BAD_REQUEST,message);
    }


    //채팅방 이름 변경

    //채팅방 목록


    //채팅방 상세

    //채팅방 나가기

    //채팅방 삭제

    //친구 초대

    //읽음 처리
}
