package com.aloc.sai.chat;

import com.aloc.sai.appointment.AppointmentRepository;
import com.aloc.sai.auth.RefreshTokenRepository;
import com.aloc.sai.chat.dto.ChatInviteRequest;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ChatInviteConcurrencyTest {
    @Autowired
    private ChatRoomService chatRoomService;
    @Autowired
    private FriendService friendService;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private ChatInviteRepository chatInviteRepository;
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

    private final List<Long> ids = new ArrayList<>();

    @BeforeEach
    void setUp() {
        cleanUp();
        ids.clear();
        for (int i = 0; i < 6; i++) {
            ids.add(userRepository.save(new User(400L + i, "동시" + i, null, "CONC000" + i)).getUserId());
        }
    }

    @AfterEach
    void cleanUp() {
        appointmentRepository.deleteAll();
        chatInviteRepository.deleteAll();
        chatRoomRepository.deleteAll();
        friendRequestRepository.deleteAll();
        friendshipRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 정원_5명_중_4명인_방에_두_명이_동시에_초대해도_정원을_넘지_않는다() throws Exception {
        Long a = ids.get(0), b = ids.get(1), c = ids.get(2), d = ids.get(3), e = ids.get(4), f = ids.get(5);
        befriend(a, b);
        befriend(a, c);
        befriend(a, d);
        befriend(a, e);
        befriend(b, f);
        Long roomId = chatRoomService.chatRoomcreate(a,
                        new CreateChatRoomRequest(ChatRoomType.PUBLIC, "모집방", null, 5, List.of(b, c, d)))
                .room().chatRoomId();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> byA = executor.submit(invite(start, a, roomId, e));
            Future<Integer> byB = executor.submit(invite(start, b, roomId, f));
            start.countDown();

            List<Integer> results = List.of(byA.get(10, TimeUnit.SECONDS), byB.get(10, TimeUnit.SECONDS));
            assertThat(results).containsExactlyInAnyOrder(200, 409);
        } finally {
            executor.shutdownNow();
        }
        assertThat(chatInviteRepository.countByChatRoom_ChatRoomId(roomId)).isEqualTo(5);
    }

    private Callable<Integer> invite(CountDownLatch start, Long inviter, Long roomId, Long invitee) {
        return () -> {
            start.await();
            try {
                chatRoomService.inviteFriends(inviter, roomId, new ChatInviteRequest(List.of(invitee)));
                return 200;
            } catch (ApiException ex) {
                return ex.getStatus().value();
            }
        };
    }

    private void befriend(Long sender, Long receiver) {
        Long requestId = friendService.sendRequest(sender, receiver).friendRequestId();
        friendService.respond(receiver, requestId, FriendRequestAction.ACCEPT);
    }
}
