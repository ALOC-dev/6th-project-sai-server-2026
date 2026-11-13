package com.aloc.sai.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRoomCloseScheduler {
    private final ChatRoomService chatRoomService;

    //생성 후 24시간이 지난 채팅방을 매분 종료한다. 실행 사이의 공백은 ChatRoom.isClosed()가 막는다.
    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void closeExpiredRooms() {
        int closedCount = chatRoomService.closeExpiredRooms();
        if (closedCount > 0) {
            log.info("24시간이 지난 채팅방 {}개를 종료했습니다.", closedCount);
        }
    }
}
