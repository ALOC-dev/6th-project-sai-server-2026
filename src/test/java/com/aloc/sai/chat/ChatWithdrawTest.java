package com.aloc.sai.chat;

import com.aloc.sai.appointment.Appointment;
import com.aloc.sai.appointment.AppointmentRepository;
import com.aloc.sai.auth.RefreshTokenRepository;
import com.aloc.sai.chat.dto.CreateChatRoomRequest;
import com.aloc.sai.common.exception.ApiException;
import com.aloc.sai.friend.FriendRequestRepository;
import com.aloc.sai.friend.FriendService;
import com.aloc.sai.friend.FriendshipRepository;
import com.aloc.sai.friend.dto.FriendRequestAction;
import com.aloc.sai.user.User;
import com.aloc.sai.user.UserRepository;
import com.aloc.sai.user.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// chatInvited·chatMessage·chatRoom(host)·appointment가 users를 참조하므로, 채팅 데이터가 있는 사용자도 탈퇴할 수 있어야 한다.
@SpringBootTest
@ActiveProfiles("test")
class ChatWithdrawTest {
    @Autowired
    private ChatRoomService chatRoomService;
    @Autowired
    private UserService userService;
    @Autowired
    private FriendService friendService;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private ChatInviteRepository chatInviteRepository;
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private FriendRequestRepository friendRequestRepository;
    @Autowired
    private FriendshipRepository friendshipRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long idA;
    private Long idB;
    private Long idC;

    @BeforeEach
    void setUp() {
        cleanUp();
        idA = userRepository.save(new User(201L, "가영", null, "WDRAAAAA")).getUserId();
        idB = userRepository.save(new User(202L, "나연", null, "WDRBBBBB")).getUserId();
        idC = userRepository.save(new User(203L, "다현", null, "WDRCCCCC")).getUserId();
        befriend(idA, idB);
        befriend(idA, idC);
    }

    @AfterEach
    void cleanUp() {
        appointmentRepository.deleteAll();
        chatMessageRepository.deleteAll();
        chatInviteRepository.deleteAll();
        chatRoomRepository.deleteAll();
        friendRequestRepository.deleteAll();
        friendshipRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 방장이_탈퇴하면_방장과_약속이_남은_참여자에게_넘어가고_메시지는_남는다() {
        Long publicId = createPublic(idA, List.of(idB, idC));
        Long directId = createDirect(idA, idB);
        sendText(publicId, idA, "안녕");

        userService.withdraw(idA, "DELETE");

        assertThat(userRepository.findById(idA)).isEmpty();
        transactionTemplate.executeWithoutResult(status -> {
            ChatRoom publicRoom = chatRoomRepository.findById(publicId).orElseThrow();
            assertThat(publicRoom.getHost().getUserId()).isIn(idB, idC);
            assertThat(publicRoom.getStatus()).isEqualTo(ChatRoomStatus.ACTIVE);

            Appointment appointment = appointmentRepository.findByChatRoom_ChatRoomId(publicId).orElseThrow();
            assertThat(appointment.getUser().getUserId()).isEqualTo(publicRoom.getHost().getUserId());

            ChatMessage message = chatMessageRepository.findAll().get(0);
            assertThat(message.getMessage()).isEqualTo("안녕");
            assertThat(message.getSender()).isNull();
        });
        assertThat(chatInviteRepository.findMembers(publicId))
                .extracting(User::getUserId).containsExactlyInAnyOrder(idB, idC);
        assertThat(chatInviteRepository.findMembers(directId))
                .extracting(User::getUserId).containsExactly(idB);
    }

    @Test
    void 혼자_남은_방에서_나간_방장이_탈퇴해도_성공하고_약속은_삭제된다() {
        Long publicId = createPublic(idA, List.of(idB));
        chatRoomService.leaveChatRoom(idB, publicId);
        chatRoomService.leaveChatRoom(idA, publicId); // 마지막 참여자로 나가 종료, 방장은 A로 남는다

        userService.withdraw(idA, "DELETE");

        assertThat(userRepository.findById(idA)).isEmpty();
        ChatRoom room = chatRoomRepository.findById(publicId).orElseThrow();
        assertThat(room.getHost()).isNull();
        assertThat(room.getStatus()).isEqualTo(ChatRoomStatus.CLOSED);
        assertThat(appointmentRepository.findByChatRoom_ChatRoomId(publicId)).isEmpty();
    }

    @Test
    void 방장이_없는_방도_상세_조회할_수_있다() {
        Long publicId = createPublic(idA, List.of(idB));
        chatRoomService.leaveChatRoom(idB, publicId);
        chatRoomService.leaveChatRoom(idA, publicId);
        userService.withdraw(idA, "DELETE");

        // 방장이 null이어도 NPE 없이 권한 확인(403)까지 간다
        assertThatThrownBy(() -> chatRoomService.getChatRoomDetail(idB, publicId))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus().value()).isEqualTo(403));
    }

    @Test
    void 방장이_나가면_약속_주인도_새_방장으로_바뀐다() {
        Long publicId = createPublic(idA, List.of(idB));

        chatRoomService.leaveChatRoom(idA, publicId);

        transactionTemplate.executeWithoutResult(status -> {
            ChatRoom room = chatRoomRepository.findById(publicId).orElseThrow();
            Appointment appointment = appointmentRepository.findByChatRoom_ChatRoomId(publicId).orElseThrow();
            assertThat(room.getHost().getUserId()).isEqualTo(idB);
            assertThat(appointment.getUser().getUserId()).isEqualTo(idB);
        });
    }

    @Test
    void 방장이_아닌_사람이_나가면_약속_주인은_그대로다() {
        Long publicId = createPublic(idA, List.of(idB, idC));

        chatRoomService.leaveChatRoom(idB, publicId);

        transactionTemplate.executeWithoutResult(status -> assertThat(
                appointmentRepository.findByChatRoom_ChatRoomId(publicId).orElseThrow().getUser().getUserId())
                .isEqualTo(idA));
    }

    @Test
    void 약속이_없는_PRIVATE_방에서_방장이_나가도_성공한다() {
        Long privateId = chatRoomService.chatRoomcreate(idA,
                        new CreateChatRoomRequest(ChatRoomType.PRIVATE, "비밀방", null, null, List.of(idB)))
                .room().chatRoomId();

        chatRoomService.leaveChatRoom(idA, privateId);

        transactionTemplate.executeWithoutResult(status -> assertThat(
                chatRoomRepository.findById(privateId).orElseThrow().getHost().getUserId()).isEqualTo(idB));
        assertThat(appointmentRepository.findByChatRoom_ChatRoomId(privateId)).isEmpty();
    }

    private Long createPublic(Long host, List<Long> invitees) {
        return chatRoomService.chatRoomcreate(host,
                        new CreateChatRoomRequest(ChatRoomType.PUBLIC, "모집방", null, 4, invitees))
                .room().chatRoomId();
    }

    private Long createDirect(Long me, Long other) {
        return chatRoomService.chatRoomcreate(me,
                        new CreateChatRoomRequest(ChatRoomType.DIRECT, null, null, null, List.of(other)))
                .room().chatRoomId();
    }

    private void sendText(Long roomId, Long senderId, String text) {
        chatMessageRepository.save(ChatMessage.text(
                chatRoomRepository.findById(roomId).orElseThrow(),
                userRepository.findById(senderId).orElseThrow(),
                text));
    }

    private void befriend(Long sender, Long receiver) {
        Long requestId = friendService.sendRequest(sender, receiver).friendRequestId();
        friendService.respond(receiver, requestId, FriendRequestAction.ACCEPT);
    }
}
