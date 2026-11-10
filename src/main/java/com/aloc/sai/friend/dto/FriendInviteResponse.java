package com.aloc.sai.friend.dto;

public record FriendInviteResponse(
        String nickname,
        String friendCode,
        String inviteUrl
) {
}
