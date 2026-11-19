package com.aloc.sai.chat;

import com.aloc.sai.chat.dto.*;
import com.aloc.sai.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatMessageService {
    //메시지 크기 제한
    private static final int MESSAGE_MAX_LENGTH=255;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatInviteRepository chatInviteRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ApplicationEventPublisher eventPublisher;

    //메시지 보내기 (검증, 저장, 커밋 후 방에)
    @Transactional
    public void send(Long userId, Long chatRoomId, ChatMessageRequest req){
        //방 확인
        ChatRoom room=chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."));

        //참여자확인
        ChatInvite invite=chatInviteRepository.findByUser_UserIdAndChatRoom_ChatRoomId(userId,chatRoomId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "채팅방 참여자가 아닙니다."));

        //종료된 방인지 확인(close or 24시)
        if(room.isClosed()){
            throw new ApiException(HttpStatus.CONFLICT, "종료된 채팅방입니다.");
        }

        //타입별로 메시지 다르게
        MessageType type=parseClientType(req.type());
        ChatMessage message=switch (type){
            case TEXT -> ChatMessage.text(room, invite.getUser(), validateText(req.message()));
            // TODO: 추천 도메인이 생기면 RECOMMEND_ROUTE 추가
            default->throw badRequest("보낼 수 없는 메시지 종류입니다.");
        };

        //저장/내가 보낸것을 가장 마지막 message읽음으로 처리
        ChatMessage saved=chatMessageRepository.save(message);
        invite.markRead(saved.getChatMessagesId());

        //커밋 후 chatmessageBoradcaster가 방에 전송됨
        eventPublisher.publishEvent(new ChatMessageSentEvent(saved.getChatMessagesId()));

    }

    //Broadcaster가 커밋 후 전송할 DTO
    @Transactional(readOnly = true)
    public ChatMessageResponse getMessage(Long chatMessageId){
        ChatMessage message=chatMessageRepository.findById(chatMessageId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "메시지를 찾을 수 없습니다."));
        return ChatMessageResponse.from(message);
    }

    //클라이언트는 TEXT, RECOMMEND_ROUTE만 보낼 수 있다. SYSTEM 서버 전용 타입은 거부
    private MessageType parseClientType(String type){
        if("TEXT".equals(type)){
            return MessageType.TEXT;
        }
        if("RECOMMEND_ROUTE".equals(type)){
            return MessageType.RECOMMEND_ROUTE;
        }
        throw badRequest("보낼 수 없는 메시지 종류입니다.");
    }

    //255자인가?
    private String validateText(String message){
        String text=(message==null)? "":message.strip();
        //이모지까지 글자 수를 세기 위해서 codePointCount를 사용한다
        int length=text.codePointCount(0,text.length());
        if (length < 1 || length > MESSAGE_MAX_LENGTH) {
            throw badRequest("메시지는 1~255자여야 합니다.");
        }
        return text;
    }

    private static final int DEFAULT_PAGE_SIZE = 30;
    private static final int MAX_PAGE_SIZE = 100;
    //이전 메시지 조회용
    @Transactional(readOnly = true)
    public ChatMessageListResponse getMessages(Long userId, Long chatRoomId,Long cursor,Integer size){
        int pageSize = (size == null) ? DEFAULT_PAGE_SIZE : size;
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw badRequest("size는 1~100이어야 합니다.");
        }
        if(!chatRoomRepository.existsById(chatRoomId)){
            throw new ApiException(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다.");
        }
        chatInviteRepository.findByUser_UserIdAndChatRoom_ChatRoomId(userId,chatRoomId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "채팅방 참여자가 아닙니다."));

        List<ChatMessage> rows = chatMessageRepository.findPage(chatRoomId, cursor, PageRequest.of(0, pageSize + 1));
        //default보다 큰 31개가 온 경우 뒤에 데이터가 더 있다는 것이기에 true임
        boolean hasNext=rows.size()>pageSize;
        //확인용으로 하나 더 가져와서 돌려줄 때는 그 하나를 빼
        List<ChatMessage> page=hasNext? rows.subList(0,pageSize):rows;

        Long nextCursor=hasNext? page.get(page.size()-1).getChatMessagesId():null;
        return new ChatMessageListResponse(
                page.stream().map(ChatMessageResponse::from).toList(),nextCursor,hasNext
        );
    }

    //서버가 보내는 입장, 퇴장 메시지
    @Transactional
    public void postSystem(ChatRoom room, String text){
        ChatMessage saved=chatMessageRepository.save(ChatMessage.system(room,text));
        eventPublisher.publishEvent(new ChatMessageSentEvent(saved.getChatMessagesId()));
    }

    //메시지 읽지 않은 수
    public record RoomUpdate(Long userId, ChatRoomUpdateResponse payload) {}

    @Transactional(readOnly = true)
    public List<RoomUpdate> getRoomUpdates(Long chatMessagesId){
        ChatMessage m = chatMessageRepository.findById(chatMessagesId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "메시지를 찾을 수 없습니다."));

        Long roomId=m.getChatRoom().getChatRoomId();
        ChatRoomListResponse.LastMessage last=new ChatRoomListResponse.LastMessage(m.getType(),m.getMessage(),m.getSendAt());

        return chatInviteRepository.findAllWithUserByChatRoomId(roomId).stream()
                .map(ci->{
                    long readId=(ci.getLastReadMessageId()==null)? 0L : ci.getLastReadMessageId();
                    int unread=(int)chatMessageRepository.countByChatRoom_ChatRoomIdAndChatMessagesIdGreaterThanAndTypeNot(roomId, readId, MessageType.SYSTEM);
                    return new RoomUpdate(ci.getUser().getUserId(),new ChatRoomUpdateResponse(roomId,last,unread));
                }).toList();
    }


    private ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
