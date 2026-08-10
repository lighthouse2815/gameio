package com.gameio.friend;

import java.util.List;

public record FriendRequestsResponse(
        List<FriendRequestResponse> incoming,
        List<FriendRequestResponse> outgoing
) {
}
