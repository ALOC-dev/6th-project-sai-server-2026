package com.aloc.sai.chat;

import com.aloc.sai.auth.RefreshTokenRepository;
import com.aloc.sai.chat.dto.*;
import com.aloc.sai.common.exception.ApiException;
import com.aloc.sai.friend.FriendRequestRepository;
import com.aloc.sai.friend.FriendService;
import com.aloc.sai.friend.FriendshipRepository;
import com.aloc.sai.friend.dto.FriendRequestAction;
import com.aloc.sai.notification.NotificationRepository;
import com.aloc.sai.user.User;
import com.aloc.sai.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@RecordApplicationEvents
class ChatMessageServiceTest {
    @Autowired
    private ChatMessageService chatMessageService;
    @Autowired
    private ChatRoomService chatRoomService;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private ChatInviteRepository chatInviteRepository;
    @Autowired
    private ChatMessageRepository chatMessageRepository;
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
    @Autowired
    private ApplicationEvents events;
    @Autowired
    private NotificationRepository notificationRepository;

    private Long idA;
    private Long idB;
    private Long idC;
    private Long roomId;   // A, B가 있는 PRIVATE 방

    @BeforeEach
    void setUp() {
        cleanUp();
        idA = userRepository.save(new User(201L, "가영", null, "MSGAAAAA")).getUserId();
        idB = userRepository.save(new User(202L, "나연", null, "MSGBBBBB")).getUserId();
        idC = userRepository.save(new User(203L, "다현", null, "MSGCCCCC")).getUserId();
        befriend(idA, idB);
        befriend(idA, idC);
        roomId = createPrivate(idA, List.of(idB));
    }

    @AfterEach
    void cleanUp() {
        // 메시지 → 참여자 → 방 순서로 지운다 (FK)
        chatMessageRepository.deleteAll();
        chatInviteRepository.deleteAll();
        chatRoomRepository.deleteAll();
        friendRequestRepository.deleteAll();
        friendshipRepository.deleteAll();
        notificationRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ───────────────────────── 메시지 보내기 ─────────────────────────

    @Test
    void 메시지를_보내면_저장되고_보낸_사람은_읽음_처리된다() {
        chatMessageService.send(idA, roomId, text("안녕"));

        List<ChatMessage> messages = chatMessageRepository.findAll();
        assertThat(messages).hasSize(1);
        ChatMessage saved = messages.get(0);
        assertThat(saved.getMessage()).isEqualTo("안녕");
        assertThat(saved.getType()).isEqualTo(MessageType.TEXT);

        ChatInvite myInvite = chatInviteRepository.findByUser_UserIdAndChatRoom_ChatRoomId(idA, roomId).orElseThrow();
        assertThat(myInvite.getLastReadMessageId()).isEqualTo(saved.getChatMessagesId());
    }

    @Test
    void 메시지를_보내면_전송_이벤트가_발행된다() {
        chatMessageService.send(idA, roomId, text("안녕"));

        Long savedId = chatMessageRepository.findAll().get(0).getChatMessagesId();
        assertThat(events.stream(ChatMessageSentEvent.class))
                .extracting(ChatMessageSentEvent::chatMessageId)
                .containsExactly(savedId);
    }

    @Test
    void 앞뒤_공백은_지우고_저장한다() {
        chatMessageService.send(idA, roomId, text("  안녕  "));

        assertThat(chatMessageRepository.findAll().get(0).getMessage()).isEqualTo("안녕");
    }

    @Test
    void 메시지_255자는_보낼_수_있다() {
        chatMessageService.send(idA, roomId, text("a".repeat(255)));

        assertThat(chatMessageRepository.count()).isEqualTo(1);
    }

    @Test
    void 빈_메시지면_400이고_저장하지_않는다() {
        assertStatus(() -> chatMessageService.send(idA, roomId, text("   ")), 400);
        assertThat(chatMessageRepository.count()).isZero();
    }

    @Test
    void 메시지가_null이면_400() {
        assertStatus(() -> chatMessageService.send(idA, roomId, text(null)), 400);
    }

    @Test
    void 메시지_256자면_400() {
        assertStatus(() -> chatMessageService.send(idA, roomId, text("a".repeat(256))), 400);
        assertThat(chatMessageRepository.count()).isZero();
    }

    @Test
    void 클라이언트가_SYSTEM_타입을_보내면_400() {
        assertStatus(() -> chatMessageService.send(idA, roomId,
                new ChatMessageRequest("SYSTEM", "가짜 안내", null)), 400);
        assertThat(chatMessageRepository.count()).isZero();
    }

    @Test
    void 없는_타입이면_400() {
        assertStatus(() -> chatMessageService.send(idA, roomId,
                new ChatMessageRequest("HELLO", "?", null)), 400);
    }

    @Test
    void 참여자가_아니면_403이고_이벤트도_발행하지_않는다() {
        assertStatus(() -> chatMessageService.send(idC, roomId, text("몰래")), 403);

        assertThat(chatMessageRepository.count()).isZero();
        assertThat(events.stream(ChatMessageSentEvent.class)).isEmpty();
    }

    @Test
    void 없는_방이면_404() {
        assertStatus(() -> chatMessageService.send(idA, 999_999L, text("안녕")), 404);
    }

    @Test
    void 생성_후_24시간이_지난_방이면_409() {
        ageRoom(roomId, 25);

        assertStatus(() -> chatMessageService.send(idA, roomId, text("안녕")), 409);
        assertThat(chatMessageRepository.count()).isZero();
    }

    // ───────────────────────── 이전 메시지 조회 ─────────────────────────

    @Test
    void 커서_없이_조회하면_최신_메시지부터_size개를_준다() {
        sendTexts(35);

        ChatMessageListResponse page = chatMessageService.getMessages(idA, roomId, null, 30);

        assertThat(page.messages()).hasSize(30);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.messages().get(0).message()).isEqualTo("메시지35");   // 가장 최신
        assertThat(page.messages().get(29).message()).isEqualTo("메시지6");
        assertThat(page.nextCursor()).isEqualTo(page.messages().get(29).chatMessageId());
    }

    @Test
    void 커서로_다음_페이지를_조회하면_남은_메시지를_주고_끝을_알린다() {
        sendTexts(35);
        Long cursor = chatMessageService.getMessages(idA, roomId, null, 30).nextCursor();

        ChatMessageListResponse page = chatMessageService.getMessages(idA, roomId, cursor, 30);

        assertThat(page.messages()).hasSize(5);
        assertThat(page.messages().get(0).message()).isEqualTo("메시지5");
        assertThat(page.messages().get(4).message()).isEqualTo("메시지1");
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void size가_없으면_30개씩_준다() {
        sendTexts(31);

        assertThat(chatMessageService.getMessages(idA, roomId, null, null).messages()).hasSize(30);
    }

    @Test
    void 메시지가_정확히_size개면_다음_페이지가_없다() {
        sendTexts(30);

        ChatMessageListResponse page = chatMessageService.getMessages(idA, roomId, null, 30);

        assertThat(page.messages()).hasSize(30);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void 메시지가_없는_방은_빈_목록() {
        ChatMessageListResponse page = chatMessageService.getMessages(idA, roomId, null, 30);

        assertThat(page.messages()).isEmpty();
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    void 다른_방의_메시지는_섞이지_않는다() {
        Long otherRoom = createPrivate(idA, List.of(idC));
        chatMessageService.send(idA, otherRoom, text("다른 방"));
        chatMessageService.send(idA, roomId, text("이 방"));

        assertThat(chatMessageService.getMessages(idA, roomId, null, 30).messages())
                .extracting(m -> m.message())
                .containsExactly("이 방");
    }

    @Test
    void 참여자가_아니면_조회할_수_없다_403() {
        assertStatus(() -> chatMessageService.getMessages(idC, roomId, null, 30), 403);
    }

    @Test
    void size가_범위를_벗어나면_400() {
        assertStatus(() -> chatMessageService.getMessages(idA, roomId, null, 0), 400);
        assertStatus(() -> chatMessageService.getMessages(idA, roomId, null, 101), 400);
    }

    @Test
    void 종료된_방도_참여자는_조회할_수_있다() {
        chatMessageService.send(idA, roomId, text("안녕"));
        ageRoom(roomId, 25);

        assertThat(chatMessageService.getMessages(idA, roomId, null, 30).messages()).hasSize(1);
    }

    // ───────────────────────── SYSTEM 메시지 ─────────────────────────

    @Test
    void 친구를_초대하면_들어왔다는_SYSTEM_메시지가_남는다() {
        chatRoomService.inviteFriends(idA, roomId, new ChatInviteRequest(List.of(idC)));

        ChatMessage system = onlyMessage();
        assertThat(system.getType()).isEqualTo(MessageType.SYSTEM);
        assertThat(system.getMessage()).isEqualTo("다현님이 들어왔습니다.");
        assertThat(system.getSender()).isNull();
    }

    @Test
    void 방에서_나가면_나갔다는_SYSTEM_메시지가_남는다() {
        chatRoomService.leaveChatRoom(idB, roomId);

        ChatMessage system = onlyMessage();
        assertThat(system.getType()).isEqualTo(MessageType.SYSTEM);
        assertThat(system.getMessage()).isEqualTo("나연님이 나갔습니다.");
    }

    @Test
    void 마지막_사람이_나가_방이_종료되면_SYSTEM_메시지를_남기지_않는다() {
        chatRoomService.leaveChatRoom(idB, roomId);   // "나연님이 나갔습니다." 1개
        chatRoomService.leaveChatRoom(idA, roomId);   // 마지막 사람 → 방 종료, 추가 메시지 없음

        assertThat(chatMessageRepository.count()).isEqualTo(1);
    }

    // ───────────────────────── 안 읽은 수 ─────────────────────────

    @Test
    void 안_읽은_수에_SYSTEM_메시지는_세지_않는다() {
        chatMessageService.send(idA, roomId, text("안녕"));
        Long lastText = chatMessageRepository.findAll().get(0).getChatMessagesId();
        chatRoomService.markAsRead(idB, roomId, new ChatReadRequest(lastText));

        chatRoomService.inviteFriends(idA, roomId, new ChatInviteRequest(List.of(idC)));   // SYSTEM 1개 추가

        assertThat(unreadOf(idB)).isZero();
    }

    @Test
    void 안_읽은_수는_내_책갈피_이후의_TEXT_메시지_수다() {
        chatMessageService.send(idA, roomId, text("하나"));
        chatMessageService.send(idA, roomId, text("둘"));

        assertThat(unreadOf(idB)).isEqualTo(2);   // B는 아직 안 읽음
        assertThat(unreadOf(idA)).isZero();       // 보낸 A는 읽음 처리됨
    }

    // ───────────────────────── 도우미 ─────────────────────────

    private ChatMessageRequest text(String message) {
        return new ChatMessageRequest("TEXT", message, null);
    }

    private void sendTexts(int count) {
        for (int i = 1; i <= count; i++) {
            chatMessageService.send(idA, roomId, text("메시지" + i));
        }
    }

    private ChatMessage onlyMessage() {
        List<ChatMessage> messages = chatMessageRepository.findAll();
        assertThat(messages).hasSize(1);
        return messages.get(0);
    }

    private int unreadOf(Long userId) {
        return chatRoomService.getChatRoom(userId, null, null).chatRooms().stream()
                .filter(r -> r.chatRoomId().equals(roomId))
                .findFirst().orElseThrow()
                .unreadCount();
    }

    private void assertStatus(Runnable action, int status) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus().value()).isEqualTo(status));
    }

    private void ageRoom(Long id, int hours) {
        ChatRoom room = chatRoomRepository.findById(id).orElseThrow();
        ReflectionTestUtils.setField(room, "createdAt", LocalDateTime.now().minusHours(hours));
        chatRoomRepository.save(room);
    }

    private Long createPrivate(Long host, List<Long> invitees) {
        return chatRoomService.chatRoomcreate(host,
                        new CreateChatRoomRequest(ChatRoomType.PRIVATE, "테스트방", null, null, invitees))
                .room().chatRoomId();
    }

    private void befriend(Long sender, Long receiver) {
        Long requestId = friendService.sendRequest(sender, receiver).friendRequestId();
        friendService.respond(receiver, requestId, FriendRequestAction.ACCEPT);
    }
}