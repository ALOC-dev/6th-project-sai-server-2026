package com.aloc.sai.user.dto;

import com.aloc.sai.user.User;

// newUser는 이번 요청에서 새로 가입했는지 여부다.
public record KakaoUserResult(User user, boolean newUser) {
}
