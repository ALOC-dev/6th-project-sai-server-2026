package com.aloc.sai.appointment;

import com.aloc.sai.appointment.dto.AppointmentConfirmRequest;
import com.aloc.sai.appointment.dto.AppointmentListResponse;
import com.aloc.sai.appointment.dto.AppointmentResponse;
import com.aloc.sai.appointment.dto.AppointmentUpdateRequest;
import com.aloc.sai.auth.RefreshTokenRepository;
import com.aloc.sai.chat.ChatInviteRepository;
import com.aloc.sai.chat.ChatMessage;
import com.aloc.sai.chat.ChatMessageRepository;
import com.aloc.sai.chat.ChatRoom;
import com.aloc.sai.chat.ChatRoomRepository;
import com.aloc.sai.chat.ChatRoomService;
import com.aloc.sai.chat.ChatRoomType;
import com.aloc.sai.chat.MessageType;
import com.aloc.sai.chat.dto.ChatRoomDetailResponse;
import com.aloc.sai.chat.dto.CreateChatRoomRequest;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AppointmentServiceTest {
    @Autowired
    private AppointmentService appointmentService;
    @Autowired
    private AppointmentRepository appointmentRepository;
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
    private NotificationRepository notificationRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private UserRepository userRepository;

    private Long idA;   // 방장 역할
    private Long idB;   // A의 친구
    private Long idC;   // A의 친구
    private Long idD;   // 아무와도 친구 아님

    private final LocalDateTime tomorrow = LocalDateTime.now().plusDays(1).withNano(0);

    @BeforeEach
    void setUp() {
        cleanUp();
        idA = userRepository.save(new User(301L, "가영", null, "APPTAAAA")).getUserId();
        idB = userRepository.save(new User(302L, "나연", null, "APPTBBBB")).getUserId();
        idC = userRepository.save(new User(303L, "다현", null, "APPTCCCC")).getUserId();
        idD = userRepository.save(new User(304L, "라희", null, "APPTDDDD")).getUserId();
        befriend(idA, idB);
        befriend(idA, idC);
    }

    @AfterEach
    void cleanUp() {
        // 다른 테이블이 가리키는 쪽(방, 사용자)을 가장 나중에 지운다 (FK)
        chatMessageRepository.deleteAll();
        appointmentRepository.deleteAll();
        chatInviteRepository.deleteAll();
        chatRoomRepository.deleteAll();
        friendRequestRepository.deleteAll();
        friendshipRepository.deleteAll();
        notificationRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ───────────────────────── 약속 확정 ─────────────────────────

    @Test
    void DIRECT_방은_두_사람_누구나_확정할_수_있고_새_약속이_생긴다() {
        Long roomId = createDirect(idA, idB);

        AppointmentResponse res = appointmentService.confirm(idB, roomId, confirm(tomorrow, tomorrow.plusHours(1)));

        assertThat(res.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(res.departAt()).isEqualTo(tomorrow);
        assertThat(res.createdBy()).isEqualTo(idB);
        assertThat(res.chatRoomName()).isEqualTo("가영");   // DIRECT는 상대 닉네임
        assertThat(res.memberCount()).isEqualTo(2);
        assertThat(appointmentRepository.count()).isEqualTo(1);
        assertThat(systemMessages(roomId)).anyMatch(m -> m.startsWith("약속이 확정되었습니다."));
    }

    @Test
    void PUBLIC_방의_모집_중_약속을_확정하면_같은_약속이_확정으로_바뀐다() {
        Long roomId = createPublic(idA, List.of(idB), 5);
        Long recruitingId = appointmentRepository.findByChatRoom_ChatRoomId(roomId).orElseThrow().getAppointmentId();

        AppointmentResponse res = appointmentService.confirm(idA, roomId, confirm(tomorrow, null));

        assertThat(res.appointmentId()).isEqualTo(recruitingId);
        assertThat(res.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(res.maxMembers()).isEqualTo(5);
        assertThat(appointmentRepository.count()).isEqualTo(1);
    }

    @Test
    void 방장이_아니면_확정할_수_없다_403() {
        Long roomId = createPrivate(idA, List.of(idB));

        assertStatus(() -> appointmentService.confirm(idB, roomId, confirm(tomorrow, null)), 403);
    }

    @Test
    void 참여자가_아니면_확정할_수_없다_403() {
        Long roomId = createDirect(idA, idB);

        assertStatus(() -> appointmentService.confirm(idC, roomId, confirm(tomorrow, null)), 403);
    }

    @Test
    void 약속_시간이_없거나_과거면_400() {
        Long roomId = createDirect(idA, idB);

        assertStatus(() -> appointmentService.confirm(idA, roomId, confirm(null, null)), 400);
        assertStatus(() -> appointmentService.confirm(idA, roomId,
                confirm(LocalDateTime.now().minusMinutes(1), null)), 400);
        assertThat(appointmentRepository.count()).isZero();
    }

    @Test
    void 끝나는_시각이_약속_시간보다_이르면_400() {
        Long roomId = createDirect(idA, idB);

        assertStatus(() -> appointmentService.confirm(idA, roomId, confirm(tomorrow, tomorrow.minusHours(1))), 400);
    }

    @Test
    void 추천_루트로_확정은_아직_지원하지_않는다_400() {
        Long roomId = createDirect(idA, idB);

        assertStatus(() -> appointmentService.confirm(idA, roomId,
                new AppointmentConfirmRequest(tomorrow, null, 104L)), 400);
    }

    @Test
    void 이미_확정된_약속을_다시_확정하면_409() {
        Long roomId = createDirect(idA, idB);
        appointmentService.confirm(idA, roomId, confirm(tomorrow, null));

        assertStatus(() -> appointmentService.confirm(idB, roomId, confirm(tomorrow.plusHours(1), null)), 409);
    }

    @Test
    void 종료된_방에서는_확정할_수_없다_409() {
        Long roomId = createDirect(idA, idB);
        ageRoom(roomId, 25);

        assertStatus(() -> appointmentService.confirm(idA, roomId, confirm(tomorrow, null)), 409);
    }

    // ───────────────────────── 약속 변경·취소 ─────────────────────────

    @Test
    void 약속_시간을_바꾸면_보낸_값만_바뀌고_안내_메시지가_남는다() {
        Long roomId = createDirect(idA, idB);
        appointmentService.confirm(idA, roomId, confirm(tomorrow, tomorrow.plusHours(2)));

        AppointmentResponse res = appointmentService.update(idA, roomId,
                new AppointmentUpdateRequest(null, tomorrow.plusHours(1), null, null));

        assertThat(res.departAt()).isEqualTo(tomorrow.plusHours(1));
        assertThat(res.returnAt()).isEqualTo(tomorrow.plusHours(2));   // 보내지 않은 값은 유지
        assertThat(systemMessages(roomId)).anyMatch(m -> m.startsWith("약속 시간이"));
    }

    @Test
    void 약속을_취소하면_CANCELED가_되고_다시_취소하면_409() {
        Long roomId = createDirect(idA, idB);
        appointmentService.confirm(idA, roomId, confirm(tomorrow, null));

        AppointmentResponse res = appointmentService.update(idA, roomId, cancel());

        assertThat(res.status()).isEqualTo(AppointmentStatus.CANCELED);
        assertThat(systemMessages(roomId)).contains("약속이 취소되었습니다.");
        assertStatus(() -> appointmentService.update(idA, roomId, cancel()), 409);
    }

    @Test
    void 취소한_약속은_다시_확정할_수_있다() {
        Long roomId = createDirect(idA, idB);
        appointmentService.confirm(idA, roomId, confirm(tomorrow, null));
        appointmentService.update(idA, roomId, cancel());

        AppointmentResponse res = appointmentService.confirm(idB, roomId, confirm(tomorrow.plusHours(3), null));

        assertThat(res.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(appointmentRepository.count()).isEqualTo(1);
    }

    @Test
    void status에_CANCELED가_아닌_값을_보내면_400() {
        Long roomId = createDirect(idA, idB);
        appointmentService.confirm(idA, roomId, confirm(tomorrow, null));

        assertStatus(() -> appointmentService.update(idA, roomId,
                new AppointmentUpdateRequest("CONFIRMED", null, null, null)), 400);
    }

    @Test
    void 바꿀_내용이_없으면_400() {
        Long roomId = createDirect(idA, idB);
        appointmentService.confirm(idA, roomId, confirm(tomorrow, null));

        assertStatus(() -> appointmentService.update(idA, roomId,
                new AppointmentUpdateRequest(null, null, null, null)), 400);
    }

    @Test
    void 약속이_없는_방을_변경하면_404() {
        Long roomId = createDirect(idA, idB);

        assertStatus(() -> appointmentService.update(idA, roomId, cancel()), 404);
    }

    @Test
    void 모집_정원은_현재_인원보다_작게_바꿀_수_없다_400() {
        Long roomId = createPublic(idA, List.of(idB, idC), 5);   // 현재 3명

        assertStatus(() -> appointmentService.update(idA, roomId,
                new AppointmentUpdateRequest(null, null, null, 2)), 400);

        AppointmentResponse res = appointmentService.update(idA, roomId,
                new AppointmentUpdateRequest(null, null, null, 3));
        assertThat(res.maxMembers()).isEqualTo(3);
    }

    @Test
    void 확정된_약속은_정원을_바꿀_수_없다_400() {
        Long roomId = createPublic(idA, List.of(idB), 5);
        appointmentService.confirm(idA, roomId, confirm(tomorrow, null));

        assertStatus(() -> appointmentService.update(idA, roomId,
                new AppointmentUpdateRequest(null, null, null, 6)), 400);
    }

    @Test
    void 방장이_아니면_변경할_수_없다_403() {
        Long roomId = createPublic(idA, List.of(idB), 5);

        assertStatus(() -> appointmentService.update(idB, roomId, cancel()), 403);
    }

    // ───────────────────────── 약속 목록 ─────────────────────────

    @Test
    void 예정된_약속에는_확정과_모집_중이_시간순으로_나오고_취소와_지난_약속은_빠진다() {
        Long later = createDirect(idA, idB);
        appointmentService.confirm(idA, later, confirm(tomorrow.plusHours(2), null));
        Long sooner = createPrivate(idA, List.of(idC));
        appointmentService.confirm(idA, sooner, confirm(tomorrow, null));
        Long recruiting = createPublic(idA, List.of(), null);           // 시간 미정 → 맨 뒤
        Long canceled = createPrivate(idA, List.of(idB));
        appointmentService.confirm(idA, canceled, confirm(tomorrow, null));
        appointmentService.update(idA, canceled, cancel());
        Long past = createPrivate(idA, List.of(idC));
        appointmentService.confirm(idA, past, confirm(tomorrow, null));
        setDepartAt(past, LocalDateTime.now().minusHours(1));

        AppointmentListResponse res = appointmentService.getAppointments(idA);

        assertThat(res.upcoming())
                .extracting(AppointmentListResponse.AppointmentItem::chatRoomId)
                .containsExactly(sooner, later, recruiting);
    }

    @Test
    void 친구가_모집_중인_약속이_보이고_참여하면_예정된_약속으로_옮겨간다() {
        Long roomId = createPublic(idA, List.of(), 5);

        AppointmentListResponse before = appointmentService.getAppointments(idB);
        assertThat(before.recruitingByFriends())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.chatRoomId()).isEqualTo(roomId);
                    assertThat(item.host().userId()).isEqualTo(idA);
                    assertThat(item.memberCount()).isEqualTo(1);
                });
        assertThat(before.upcoming()).isEmpty();

        chatRoomService.joinRecruitingRoom(idB, roomId);

        AppointmentListResponse after = appointmentService.getAppointments(idB);
        assertThat(after.recruitingByFriends()).isEmpty();
        assertThat(after.upcoming()).extracting(AppointmentListResponse.AppointmentItem::chatRoomId)
                .containsExactly(roomId);
    }

    @Test
    void 친구가_아니면_모집_중_약속이_보이지_않는다() {
        createPublic(idA, List.of(), 5);

        assertThat(appointmentService.getAppointments(idD).recruitingByFriends()).isEmpty();
    }

    @Test
    void 정원이_찬_모집_중_약속은_보이지_않는다() {
        createPublic(idA, List.of(idB), 2);   // A, B로 가득 참

        assertThat(appointmentService.getAppointments(idC).recruitingByFriends()).isEmpty();
    }

    // ───────────────────────── 모집 중 약속방 참여 ─────────────────────────

    @Test
    void 친구의_모집_중_약속방에_참여하면_상세를_돌려주고_입장_메시지가_남는다() {
        Long roomId = createPublic(idA, List.of(), 5);

        ChatRoomDetailResponse res = chatRoomService.joinRecruitingRoom(idB, roomId);

        assertThat(res.chatRoomId()).isEqualTo(roomId);
        assertThat(res.memberCount()).isEqualTo(2);
        assertThat(res.members()).extracting(ChatRoomDetailResponse.Member::userId).contains(idB);
        assertThat(res.appointment().status()).isEqualTo(AppointmentStatus.RECRUITING);
        assertThat(systemMessages(roomId)).contains("나연님이 들어왔습니다.");
    }

    @Test
    void 이미_참여_중이면_그대로_200이고_중복으로_들어가지_않는다() {
        Long roomId = createPublic(idA, List.of(idB), 5);

        ChatRoomDetailResponse res = chatRoomService.joinRecruitingRoom(idB, roomId);

        assertThat(res.memberCount()).isEqualTo(2);
        assertThat(chatMessageRepository.count()).isZero();
    }

    @Test
    void 방장의_친구가_아니면_참여할_수_없다_403() {
        Long roomId = createPublic(idA, List.of(), 5);

        assertStatus(() -> chatRoomService.joinRecruitingRoom(idD, roomId), 403);
    }

    @Test
    void PUBLIC_방이_아니면_참여할_수_없다_403() {
        Long roomId = createPrivate(idA, List.of(idB));

        assertStatus(() -> chatRoomService.joinRecruitingRoom(idC, roomId), 403);
    }

    @Test
    void 정원이_가득_차면_참여할_수_없다_409() {
        Long roomId = createPublic(idA, List.of(idB), 2);

        assertStatus(() -> chatRoomService.joinRecruitingRoom(idC, roomId), 409);
    }

    @Test
    void 모집이_끝난_약속방에는_참여할_수_없다_409() {
        Long roomId = createPublic(idA, List.of(), 5);
        appointmentService.confirm(idA, roomId, confirm(tomorrow, null));

        assertStatus(() -> chatRoomService.joinRecruitingRoom(idB, roomId), 409);
    }

    // ───────────────────────── 도우미 ─────────────────────────

    private AppointmentConfirmRequest confirm(LocalDateTime departAt, LocalDateTime returnAt) {
        return new AppointmentConfirmRequest(departAt, returnAt, null);
    }

    private AppointmentUpdateRequest cancel() {
        return new AppointmentUpdateRequest("CANCELED", null, null, null);
    }

    private List<String> systemMessages(Long roomId) {
        return chatMessageRepository.findAll().stream()
                .filter(m -> m.getType() == MessageType.SYSTEM)
                .filter(m -> m.getChatRoom().getChatRoomId().equals(roomId))
                .map(ChatMessage::getMessage)
                .toList();
    }

    private void assertStatus(Runnable action, int status) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus().value()).isEqualTo(status));
    }

    private void ageRoom(Long roomId, int hours) {
        ChatRoom room = chatRoomRepository.findById(roomId).orElseThrow();
        ReflectionTestUtils.setField(room, "createdAt", LocalDateTime.now().minusHours(hours));
        chatRoomRepository.save(room);
    }

    private void setDepartAt(Long roomId, LocalDateTime departAt) {
        Appointment appointment = appointmentRepository.findByChatRoom_ChatRoomId(roomId).orElseThrow();
        ReflectionTestUtils.setField(appointment, "departAt", departAt);
        appointmentRepository.save(appointment);
    }

    private Long createDirect(Long host, Long invitee) {
        return create(host, new CreateChatRoomRequest(ChatRoomType.DIRECT, null, null, null, List.of(invitee)));
    }

    private Long createPrivate(Long host, List<Long> invitees) {
        return create(host, new CreateChatRoomRequest(ChatRoomType.PRIVATE, "비밀방", null, null, invitees));
    }

    private Long createPublic(Long host, List<Long> invitees, Integer maxMembers) {
        return create(host, new CreateChatRoomRequest(ChatRoomType.PUBLIC, "모집방", null, maxMembers, invitees));
    }

    private Long create(Long host, CreateChatRoomRequest request) {
        return chatRoomService.chatRoomcreate(host, request).room().chatRoomId();
    }

    private void befriend(Long sender, Long receiver) {
        Long requestId = friendService.sendRequest(sender, receiver).friendRequestId();
        friendService.respond(receiver, requestId, FriendRequestAction.ACCEPT);
    }
}