package com.aloc.sai.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {
    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void deleteExpiredTokens() {
        int deletedCount = refreshTokenRepository.deleteExpired(LocalDateTime.now());
        log.info("만료된 리프레시 토큰 {}개를 삭제했습니다.", deletedCount);
    }
}
