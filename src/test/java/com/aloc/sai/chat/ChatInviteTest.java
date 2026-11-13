package com.aloc.sai.chat;

import com.aloc.sai.auth.RefreshTokenRepository;
import com.aloc.sai.chat.dto.ChatInviteRequest;
import com.aloc.sai.chat.dto.ChatInviteResponse;
import com.aloc.sai.chat.dto.CreateChatRoomRequest;
import com.aloc.sai.common.exception.ApiException;
import com.aloc.sai.friend.FriendRequestRepository;
import com.aloc.sai.friend.FriendService;
import com.aloc.sai.friend.FriendshipRepository;
import com.aloc.sai.friend.dto.FriendRequestAction;
import com.aloc.sai.user.User;
import com.aloc.sai.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ChatInviteTest {
    @Autowired
    private ChatRoomService chatRoomService;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private ChatInviteRepository chatInviteRepository;
    @Autowired
    private FriendService friendService;
    @Autowired
    private FriendRequestRepository friendRequestRepository;
    @Autowired
    private FriendshipRepository friendshipRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private Long idA;
    private Long idB;
    private Long idC;
    private Long idD;

    @BeforeEach
    void setUp() {
        cleanUp();
        idA = userRepository.save(new User(101L, "가영", null, "CHATAAAA")).getUserId();
        idB = userRepository.save(new User(102L, "나연", null, "CHATBBBB")).getUserId();
        idC = userRepository.save(new User(103L, "다현", null, "CHATCCCC")).getUserId();
        idD = userRepository.save(new User(104L, "라희", null, "CHATDDDD")).getUserId();
        befriend(idA, idB);
        befriend(idA, idC);
    }

    @AfterEach
    void cleanUp() {
        chatInviteRepository.deleteAll();
        chatRoomRepository.deleteAll();
        friendRequestRepository.deleteAll();
        friendshipRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 일대일_방에서_초대하면_이름_없는_새_PRIVATE_방을_만든다() {
        Long directId = createDirect(idA, idB);

        ChatInviteResponse response = chatRoomService.inviteFriends(idA, directId, new ChatInviteRequest(List.of(idC)));

        assertThat(response.converted()).isTrue();
        assertThat(response.type()).isEqualTo(ChatRoomType.PRIVATE);
        assertThat(response.chatRoomId()).isNotEqualTo(directId);
        assertThat(response.memberCount()).isEqualTo(3);

        ChatRoom privateRoom = chatRoomRepository.findById(response.chatRoomId()).orElseThrow();
        assertThat(privateRoom.getChatRoomName()).isNull();
        assertThat(chatInviteRepository.findMembers(privateRoom.getChatRoomId()))
                .extracting(User::getUserId)
                .containsExactlyInAnyOrder(idA, idB, idC);
        // 기존 1:1 방은 그대로 둔다
        assertThat(chatInviteRepository.countByChatRoom_ChatRoomId(directId)).isEqualTo(2);
    }

    @Test
    void 일대일_방에서_친구가_아닌_사용자를_초대하면_403() {
        Long directId = createDirect(idA, idB);

        assertThatThrownBy(() -> chatRoomService.inviteFriends(idA, directId, new ChatInviteRequest(List.of(idD))))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus().value()).isEqualTo(403));
        assertThat(chatRoomRepository.count()).isEqualTo(1);
    }

    @Test
    void 일대일_방에서_이미_참여_중인_사람만_초대하면_400() {
        Long directId = createDirect(idA, idB);

        assertThatThrownBy(() -> chatRoomService.inviteFriends(idA, directId, new ChatInviteRequest(List.of(idB))))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus().value()).isEqualTo(400));
    }

    private Long createDirect(Long me, Long other) {
        return chatRoomService.chatRoomcreate(me,
                        new CreateChatRoomRequest(ChatRoomType.DIRECT, null, null, null, List.of(other)))
                .room().chatRoomId();
    }

    private void befriend(Long sender, Long receiver) {
        Long requestId = friendService.sendRequest(sender, receiver).friendRequestId();
        friendService.respond(receiver, requestId, FriendRequestAction.ACCEPT);
    }
}
