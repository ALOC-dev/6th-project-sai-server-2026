package com.aloc.sai.friend;

import com.aloc.sai.user.User;
import com.aloc.sai.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

// 설정값이 "/"로 끝나도 초대 링크에 "//"가 생기지 않는지 확인한다.
@SpringBootTest(properties = "friend.invite-base-url=https://example.com/invite//")
@ActiveProfiles("test")
class FriendInviteUrlTest {
    @Autowired
    private FriendService friendService;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    @Test
    void 설정값_끝의_슬래시를_지우고_링크를_만든다() {
        User user = userRepository.save(new User(100L, "링크테스트", null, "LINK0001"));

        assertThat(friendService.getMyInvite(user.getUserId()).inviteUrl())
                .isEqualTo("https://example.com/invite/LINK0001");
    }
}
