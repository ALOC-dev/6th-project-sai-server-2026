package com.aloc.sai.chat;

import com.aloc.sai.common.exception.ApiException;
import com.aloc.sai.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

// 다른 도메인(약속 등)이 채팅방 정보를 볼 때 쓴다. 채팅 Repository만 써서 순환 참조가 생기지 않는다
@Service
@RequiredArgsConstructor
public class ChatRoomQueryService {
    private final ChatRoomRepository chatRoomRepository;
    private final ChatInviteRepository chatInviteRepository;

    @Transactional(readOnly = true)
    public ChatRoom getRoomForUpdate(Long chatRoomId){
        return chatRoomRepository.findByIdForUpdate(chatRoomId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public boolean isMember(Long userId, Long chatRoomId){
        return chatInviteRepository.findByUser_UserIdAndChatRoom_ChatRoomId(userId, chatRoomId).isPresent();
    }

    @Transactional(readOnly = true)
    public int countMembers(Long chatRoomId) {
        return (int) chatInviteRepository.countByChatRoom_ChatRoomId(chatRoomId);
    }

    @Transactional(readOnly = true)
    public List<Long> getMyRoomIds(Long userId) {
        return chatInviteRepository.findAllByUserId(userId).stream()
                .map(ci -> ci.getChatRoom().getChatRoomId())
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<User> findOpponent(Long chatRoomId, Long userId) {
        return chatInviteRepository.findOpponent(chatRoomId, userId).map(ChatInvite::getUser);
    }
}
