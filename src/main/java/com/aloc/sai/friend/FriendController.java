package com.aloc.sai.friend;

import com.aloc.sai.friend.dto.FriendListResponse;
import com.aloc.sai.friend.dto.FriendRequestCreateRequest;
import com.aloc.sai.friend.dto.FriendRequestCreateResponse;
import com.aloc.sai.friend.dto.FriendRequestListResponse;
import com.aloc.sai.friend.dto.FriendRequestRespondRequest;
import com.aloc.sai.friend.dto.FriendRequestRespondResponse;
import com.aloc.sai.security.LoginUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FriendController {
    private final FriendService friendService;

    @PostMapping("/friend-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public FriendRequestCreateResponse sendRequest(
            @LoginUserId Long userId,
            @Valid @RequestBody FriendRequestCreateRequest request
    ) {
        return friendService.sendRequest(userId, request.receiverId());
    }

    @GetMapping("/friend-requests")
    public FriendRequestListResponse getRequests(@LoginUserId Long userId) {
        return friendService.getRequests(userId);
    }

    @PatchMapping("/friend-requests/{friendRequestId}")
    public FriendRequestRespondResponse respond(
            @LoginUserId Long userId,
            @PathVariable Long friendRequestId,
            @Valid @RequestBody FriendRequestRespondRequest request
    ) {
        return friendService.respond(userId, friendRequestId, request.action());
    }

    @GetMapping("/friends")
    public FriendListResponse getFriends(
            @LoginUserId Long userId,
            @RequestParam(defaultValue = "false") boolean available
    ) {
        return friendService.getFriends(userId, available);
    }

    @DeleteMapping("/friends/{friendId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFriend(@LoginUserId Long userId, @PathVariable Long friendId) {
        friendService.deleteFriend(userId, friendId);
    }
}
