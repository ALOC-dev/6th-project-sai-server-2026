package com.aloc.sai.notification;

import com.aloc.sai.notification.dto.DeviceRegisterResult;
import com.aloc.sai.notification.dto.DeviceResponse;
import com.aloc.sai.user.User;
import com.aloc.sai.user.UserService;
import com.aloc.sai.user.UserWithdrawEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserDeviceService {
    private final UserService userService;
    private final UserDeviceRepository userDeviceRepository;
    private final TransactionTemplate transactionTemplate;

    // 토큰이 이미 있으면 주인·플랫폼·갱신 시각을 바꾸고, 없으면 새로 만든다.
    // 앱 실행과 로그인 때 같은 토큰으로 동시에 들어오면 둘 다 "없음"으로 보고 넣다가 UNIQUE에 걸린다.
    // 이때 실패한 쪽은 트랜잭션이 롤백되므로, 새 트랜잭션에서 한 번 더 시도해 갱신으로 처리한다.
    // 사용자 행을 잠그고 시작하므로 탈퇴와 겹쳐도 차례로 처리되고, 탈퇴 뒤에 온 등록은 404다.
    // 그래서 재시도까지 가는 제약 위반은 pushToken UNIQUE 충돌뿐이다.
    public DeviceRegisterResult register(Long userId, String pushToken, DevicePlatform platform) {
        try {
            return transactionTemplate.execute(status -> upsert(userId, pushToken, platform));
        } catch (DataIntegrityViolationException e) {
            return transactionTemplate.execute(status -> upsert(userId, pushToken, platform));
        }
    }

    // 로그아웃한 기기로 푸시가 가지 않도록 지운다. 본인 기기가 아니거나 없는 토큰이면 아무 것도 하지 않는다.
    @Transactional
    public void unregister(Long userId, String pushToken) {
        userDeviceRepository.deleteByPushTokenAndUserId(pushToken, userId);
    }

    @EventListener
    @Transactional
    public void deleteAllOnWithdraw(UserWithdrawEvent event) {
        userDeviceRepository.deleteAllByUserId(event.userId());
    }

    private DeviceRegisterResult upsert(Long userId, String pushToken, DevicePlatform platform) {
        User user = userService.getUserForUpdate(userId);
        LocalDateTime now = LocalDateTime.now();
        Optional<UserDevice> existing = userDeviceRepository.findByPushToken(pushToken);
        if (existing.isPresent()) {
            UserDevice device = existing.get();
            device.reregister(user, platform, now);
            userDeviceRepository.flush();
            return new DeviceRegisterResult(DeviceResponse.from(device), false);
        }

        UserDevice device = userDeviceRepository.saveAndFlush(new UserDevice(user, pushToken, platform, now));
        return new DeviceRegisterResult(DeviceResponse.from(device), true);
    }
}
