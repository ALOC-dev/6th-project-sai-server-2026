package com.aloc.sai.notification.dto;

// 새로 등록했으면 201, 기존 토큰을 갱신했으면 200으로 응답하기 위해 함께 돌려준다.
public record DeviceRegisterResult(DeviceResponse device, boolean created) {
}
