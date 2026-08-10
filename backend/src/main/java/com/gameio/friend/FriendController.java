package com.gameio.friend;

import com.gameio.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/friends")
public class FriendController {
    private static final String UUID_PATTERN =
            "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private final FriendService friendService;
    private final CurrentUser currentUser;

    public FriendController(FriendService friendService, CurrentUser currentUser) {
        this.friendService = friendService;
        this.currentUser = currentUser;
    }

    @GetMapping
    List<FriendResponse> list(Authentication authentication) {
        return friendService.listFriends(currentUser.id(authentication));
    }

    @GetMapping("/requests")
    FriendRequestsResponse listRequests(Authentication authentication) {
        return friendService.listRequests(currentUser.id(authentication));
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    FriendRequestResponse send(
            Authentication authentication,
            @Valid @RequestBody SendFriendRequest request) {
        return friendService.sendRequest(currentUser.id(authentication), request);
    }

    @PostMapping("/requests/{requestId}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void accept(
            Authentication authentication,
            @PathVariable @Pattern(regexp = UUID_PATTERN, message = "must be a UUID") String requestId) {
        friendService.accept(currentUser.id(authentication), UUID.fromString(requestId));
    }

    @PostMapping("/requests/{requestId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reject(
            Authentication authentication,
            @PathVariable @Pattern(regexp = UUID_PATTERN, message = "must be a UUID") String requestId) {
        friendService.reject(currentUser.id(authentication), UUID.fromString(requestId));
    }

    @DeleteMapping("/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(
            Authentication authentication,
            @PathVariable
            @Pattern(regexp = "[A-Za-z0-9_]{3,24}", message = "must contain 3-24 letters, numbers, or underscores")
            String username) {
        friendService.remove(currentUser.id(authentication), username);
    }
}
