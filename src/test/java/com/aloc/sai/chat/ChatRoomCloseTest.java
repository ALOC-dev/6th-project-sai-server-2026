package com.aloc.sai.chat;

import com.aloc.sai.auth.RefreshTokenRepository;
import com.aloc.sai.chat.dto.ChatInviteRequest;
import com.aloc.sai.chat.dto.ChatUpdateRequest;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ChatRoomCloseTest {
    @Autowired
    private ChatRoomService chatRoomService;
    @Autowired
    private FriendService friendService;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private ChatInviteRepository chatInviteRepository;
    @Autowired
    private FriendRequestRepository friendRequestRepository;
    @Autowired
    private FriendshipRepository friendshipRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private UserRepository userRepository;

    private Long idA;
    private Long idB;
    private Long idC;

    @BeforeEach
    void setUp() {
        cleanUp();
        idA = userRepository.save(new User(301L, "가영", null, "CLSAAAAA")).getUserId();
        idB = userRepository.save(new User(302L, "나연", null, "CLSBBBBB")).getUserId();
        idC = userRepository.save(new User(303L, "다현", null, "CLSCCCCC")).getUserId();
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
    void 생성_후_24시간이_지난_방만_종료한다() {
        Long expiredId = createPrivate(idA, List.of(idB));
        Long freshId = createPrivate(idA, List.of(idB));
        ageRoom(expiredId, 25);
        ageRoom(freshId, 23);

        int closed = chatRoomService.closeExpiredRooms();

        assertThat(closed).isEqualTo(1);
        assertThat(chatRoomRepository.findById(expiredId).orElseThrow().getStatus()).isEqualTo(ChatRoomStatus.CLOSED);
        assertThat(chatRoomRepository.findById(freshId).orElseThrow().getStatus()).isEqualTo(ChatRoomStatus.ACTIVE);
    }

    @Test
    void 스케줄러가_돌기_전이라도_24시간이_지난_방은_변경할_수_없다() {
        Long roomId = createPrivate(idA, List.of(idB));
        ageRoom(roomId, 25); // 상태는 아직 ACTIVE

        assertThatThrownBy(() -> chatRoomService.updateChatRoom(idA, roomId, new ChatUpdateRequest("새 이름")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(409));
        assertThatThrownBy(() -> chatRoomService.inviteFriends(idA, roomId, new ChatInviteRequest(List.of(idC))))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(409));

        assertThat(chatRoomService.getChatRoom(idA, null, null).chatRooms()).isEmpty();
        assertThat(chatRoomService.getChatRoom(idA, "CLOSED", null).chatRooms()).hasSize(1);
    }

    @Test
    void 종료_작업은_매분_실행된다() throws Exception {
        Scheduled scheduled = ChatRoomCloseScheduler.class
                .getMethod("closeExpiredRooms")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("0 * * * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }

    private void ageRoom(Long roomId, int hours) {
        ChatRoom room = chatRoomRepository.findById(roomId).orElseThrow();
        ReflectionTestUtils.setField(room, "createdAt", LocalDateTime.now().minusHours(hours));
        chatRoomRepository.save(room);
    }

    private Long createPrivate(Long host, List<Long> invitees) {
        return chatRoomService.chatRoomcreate(host,
                        new CreateChatRoomRequest(ChatRoomType.PRIVATE, "비밀방", null, null, invitees))
                .room().chatRoomId();
    }

    private void befriend(Long sender, Long receiver) {
        Long requestId = friendService.sendRequest(sender, receiver).friendRequestId();
        friendService.respond(receiver, requestId, FriendRequestAction.ACCEPT);
    }
}
