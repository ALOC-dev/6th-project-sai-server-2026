package com.aloc.sai.chat;

import com.aloc.sai.appointment.AppointmentService;
import com.aloc.sai.chat.dto.*;
import com.aloc.sai.common.exception.ApiException;
import com.aloc.sai.appointment.Appointment;
import com.aloc.sai.friend.FriendService;
import com.aloc.sai.user.User;
import com.aloc.sai.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatRoomService {
    //친구들만 채팅 가능
    //public: 친구들에게 모두 보이는 채팅방
    //private: 선택한 친구들만 모인 채팅방
    //direct: 1:1

    private static final int NAME_MAX_LENGTH = 20;
    private static final int MIN_MEMBERS = 2;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatInvitedRepository chatInvitedRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserService userService;
    private final AppointmentService appointmentService;
    private final FriendService friendService;

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

        User me =userService.getUser(userId);
        List<User> invitees = inviteeIds.stream()
                .map(userService::getUser)
                .toList();

        //친구만 초대 가능
        for (Long inviteeId : inviteeIds) {
            if (!friendService.areFriends(userId, inviteeId)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "친구만 채팅방에 초대할 수 있습니다.");
            }
        }

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
        Appointment appointment=(type==ChatRoomType.PUBLIC)
                ? appointmentService.createRecruiting(room, me, req.maxMembers(), req.departAt())
                : null;

        //TODO:초대 알림구현

        return new CreateChatRoomResult(ChatRoomResponse.of(room,members,appointment),true);
    }

    private ApiException badRequest(String message){
        return new ApiException(HttpStatus.BAD_REQUEST,message);
    }


    //방장이 채팅방 이름 변경
    @Transactional
    public ChatUpdateResponse updateChatRoom(Long userId, Long chatRoomId, ChatUpdateRequest req){
        ChatRoom room=chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."));

        if (room.getType() == ChatRoomType.DIRECT) {
            throw badRequest("1:1 채팅방은 이름을 변경할 수 없습니다.");
        }
        if (room.getStatus() == ChatRoomStatus.CLOSED) {
            throw new ApiException(HttpStatus.CONFLICT, "종료된 채팅방입니다.");
        }
        if(!room.isHost(userId)){
            throw new ApiException(HttpStatus.FORBIDDEN, "방장만 채팅방 이름을 바꿀 수 있습니다.");
        }
        room.rename(req.chatRoomName());
        return ChatUpdateResponse.from(room);
    }

    //채팅방 목록
    @Transactional(readOnly = true)
    public ChatRoomListResponse getChatRoom(Long userId, String status, ChatRoomType type){
        List<ChatInvite> myInvites=chatInvitedRepository.findAllByUserId(userId);

        List<ChatRoomListResponse.ChatRoomSummary> summaries = myInvites.stream()
                .filter(invite -> matchesStatus(invite.getChatRoom(),status))
                .filter(invite -> type == null || invite.getChatRoom().getType() == type)
                .map(invite -> toSummary(invite.getChatRoom(), userId, invite.getLastReadMessageId()))
                .sorted(latestMessageFirst())
                .toList();

        return new ChatRoomListResponse(summaries);
    }

    private ChatRoomListResponse.ChatRoomSummary toSummary(ChatRoom room, Long userId, Long lastReadId){
        Long roomId = room.getChatRoomId();
        int memberCount=(int) chatInvitedRepository.countByChatRoomId(roomId);

        ChatRoomListResponse.LastMessage lastMessage = chatMessageRepository
                .findTopByChatRoomIdOrderByChatMessagesIdDesc(roomId)
                .map(m -> new ChatRoomListResponse.LastMessage(m.getType(), m.getMessage(), m.getSendAt()))
                .orElse(null);

        long readId = (lastReadId == null) ? 0L : lastReadId;
        int unreadCount = (int) chatMessageRepository
                .countByChatRoomIdAndChatMessagesIdGreaterThan(roomId, readId);

        ChatRoomListResponse.Opponent opponent=null;
        if(room.getType()==ChatRoomType.DIRECT){
            opponent=chatInvitedRepository.findOpponent(roomId,userId)
                    .map(ci->new ChatRoomListResponse.Opponent(
                            ci.getUser().getUserId(),
                            ci.getUser().getNickname(),
                            ci.getUser().getProfileImageUrl()
                    )).orElse(null);
        }

        return ChatRoomListResponse.ChatRoomSummary.of(room, memberCount, opponent, lastMessage, unreadCount);
    }

    private boolean matchesStatus(ChatRoom room, String status) {
        if (status == null || status.equals("ACTIVE")) return room.getStatus() == ChatRoomStatus.ACTIVE;
        if (status.equals("CLOSED")) return room.getStatus() == ChatRoomStatus.CLOSED;
        if (status.equals("ALL")) return true;
        throw new ApiException(HttpStatus.BAD_REQUEST, "채팅방 상태 값이 올바르지 않습니다.");
    }

    private Comparator<ChatRoomListResponse.ChatRoomSummary> latestMessageFirst() {
        return Comparator.comparing(
                (ChatRoomListResponse.ChatRoomSummary s) ->
                        s.lastMessage() == null ? null : s.lastMessage().sendAt(),
                Comparator.nullsLast(Comparator.reverseOrder()));
    }

    //채팅방 상세
    @Transactional(readOnly = true)
    public ChatRoomDetailResponse getChatRoomDetail(Long userId, Long chatRoomId){
        ChatRoom room=chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."));
        //참여자
        List<ChatInvite> invites=chatInvitedRepository.findAllWithUserByChatRoomId(chatRoomId);
        boolean isMember=invites.stream()
                    .anyMatch(ci->ci.getUser().getUserId().equals(userId));
        if (!isMember && !canViewAsFriend(room, userId)) {//친구 or 채팅방 참여자만 볼 수 있음
            throw new ApiException(HttpStatus.FORBIDDEN, "채팅방 참여자만 볼 수 있습니다.");
        }

        //방장
        Long hostId=(room.getType()==ChatRoomType.DIRECT)? null: room.getHost().getUserId();

        //참여자 목록
        List<ChatRoomDetailResponse.Member> members=invites.stream()
                .map(ci->new ChatRoomDetailResponse.Member(
                        ci.getUser().getUserId(),
                        ci.getUser().getNickname(),
                        ci.getUser().getProfileImageUrl(),
                        ci.getUser().getUserId().equals(hostId)
                ))
                .toList();

        //약속
        ChatRoomDetailResponse.AppointmentSummary appointment=appointmentService.findByChatRoomId(chatRoomId)
                .map(ChatRoomDetailResponse.AppointmentSummary::from)
                .orElse(null);

        return new ChatRoomDetailResponse(
                room.getChatRoomId(),
                room.getChatRoomName(),
                room.getType(),
                room.getStatus(),
                hostId,
                members.size(),//현재 참여인원
                members,
                appointment,
                room.getCreatedAt(),
                room.getCreatedAt().plusHours(24)
        );

    }

    private boolean canViewAsFriend(ChatRoom room, Long userId){
        return room.getType()==ChatRoomType.PUBLIC
                && friendService.areFriends(userId, room.getHost().getUserId());
    }

    //채팅방 나가기
    @Transactional
    public void leaveChatRoom(Long userId,Long chatRoomId){
        ChatRoom room=chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."));

        //참여자
        List<ChatInvite> invites = chatInvitedRepository.findAllWithUserByChatRoomId(chatRoomId);
        ChatInvite myInvite = invites.stream()
                .filter(ci -> ci.getUser().getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "채팅방 참여자가 아닙니다."));

        chatInvitedRepository.delete(myInvite);

        //남은 참여자
        List<ChatInvite> remaining=invites.stream()
                .filter(ci->!ci.getUser().getUserId().equals(userId))
                .toList();

        //모든 참여자 나가면 방 종료
        if(remaining.isEmpty()){
            room.close();
            return;
        }

        //방장이 나가면 남은 사람에게 넘김 (DIRECT는 방장 개념 없음)
        if (room.getType() != ChatRoomType.DIRECT && room.isHost(userId)) {
            room.changeHost(remaining.get(0).getUser());
        }

        //TODO:채팅방 나가는 알림
    }

    //친구 초대
    @Transactional
    public ChatInviteResponse inviteFriends(Long userId, Long chatRoomId, ChatInviteRequest req){
        ChatRoom room=chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."));

        if (room.getStatus() == ChatRoomStatus.CLOSED) {
            throw new ApiException(HttpStatus.CONFLICT, "종료된 채팅방입니다.");
        }

        List<ChatInvite> invites=chatInvitedRepository.findAllWithUserByChatRoomId(chatRoomId);
        Set<Long> memberIds=invites.stream()
                .map(ci->ci.getUser().getUserId())
                .collect(Collectors.toSet());
        if(!memberIds.contains(userId)){
            throw new ApiException(HttpStatus.FORBIDDEN, "채팅방 참여자가 아닙니다.");
        }

        //이미참여중인 사람을 제외한 나머지 초대받은 사람
        List<Long> newIds=req.inviteeIds().stream()
                .filter(id->id!=null && !memberIds.contains(id))
                .distinct()
                .toList();

        for(Long id:newIds){
            if(!friendService.areFriends(userId,id)){
                throw new ApiException(HttpStatus.FORBIDDEN, "친구만 초대할 수 있습니다.");
            }
        }
        List<User> newUsers = newIds.stream().map(userService::getUser).toList();

        //DIRECT 방: 기존 방은 두고, 기존 두 명 + 초대한 친구로 새 PRIVATE 방
        if(room.getType()==ChatRoomType.DIRECT){
            if(newUsers.isEmpty()){
                throw badRequest("초대할 친구를 선택해 주세요.");
            }
            User me = userService.getUser(userId);
            ChatRoom privateRoom = chatRoomRepository.save(
                    ChatRoom.create(ChatRoomType.PRIVATE, null, me, null));

            List<User>members=new ArrayList<>(invites.stream().map(ChatInvite::getUser).toList());
            members.addAll(newUsers);
            chatInvitedRepository.saveAll(members.stream().map(u -> ChatInvite.of(u, privateRoom)).toList());

            //TODO: 초대 알림(CHAT_INVITE)

            return new ChatInviteResponse(privateRoom.getChatRoomId(), true, ChatRoomType.PRIVATE,
                    newIds, members.size());
        }

        //PUBLIC 방: 정원 확인
        if(room.getType()==ChatRoomType.PUBLIC){
            Integer max=appointmentService.findByChatRoomId(chatRoomId)
                    .map(Appointment::getMaxMembers)
                    .orElse(null);

            if(max!=null && memberIds.size()+newUsers.size()>max){
                throw new ApiException(HttpStatus.CONFLICT, "모집 정원을 넘어 초대할 수 없습니다.");
            }
        }

        chatInvitedRepository.saveAll(newUsers.stream().map(u->ChatInvite.of(u,room)).toList());
        for (User u:newUsers){
            //TODO:초대 알림
        }

        return new ChatInviteResponse(room.getChatRoomId(),false,room.getType(),
                newIds, memberIds.size()+newUsers.size());
    }

    //읽음 처리
    @Transactional
    public ChatReadResponse markAsRead(Long userId, Long chatRoomId,ChatReadRequest req){
        ChatRoom room=chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."));

        ChatInvite myInvite=chatInvitedRepository.findByUser_UserIdAndChatRoom_ChatRoomId(userId,chatRoomId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "채팅방 참여자가 아닙니다."));

        Long messageId = req.lastReadMessageId();
        if (!chatMessageRepository.existsByChatMessagesIdAndChatRoomId(messageId, chatRoomId)) {
            throw badRequest("이 채팅방의 메시지가 아닙니다.");
        }

        myInvite.markRead(messageId);

        int unreadCount = (int) chatMessageRepository
                .countByChatRoomIdAndChatMessagesIdGreaterThan(chatRoomId, myInvite.getLastReadMessageId());

        return new ChatReadResponse(chatRoomId,myInvite.getLastReadMessageId(),unreadCount);
    }
}
