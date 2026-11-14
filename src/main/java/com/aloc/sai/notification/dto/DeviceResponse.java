package com.aloc.sai.notification.dto;

import com.aloc.sai.notification.DevicePlatform;
import com.aloc.sai.notification.UserDevice;

import java.time.LocalDateTime;

public record DeviceResponse(
        Long deviceId,
        DevicePlatform platform,
        LocalDateTime updatedAt
) {
    public static DeviceResponse from(UserDevice device) {
        return new DeviceResponse(device.getDeviceId(), device.getPlatform(), device.getUpdatedAt());
    }
}
