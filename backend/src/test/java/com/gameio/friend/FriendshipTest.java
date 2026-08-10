package com.gameio.friend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gameio.user.UserAccount;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FriendshipTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-10T00:00:00Z");

    @Test
    void canonicalizesThePairWithoutLosingRequestDirection() {
        UserAccount requester = user("CanonicalOne", "canonical-one@example.com");
        UserAccount recipient = user("CanonicalTwo", "canonical-two@example.com");

        Friendship friendship = Friendship.pending(requester, recipient, CREATED_AT);

        assertThat(friendship.getUserLow().getId().toString())
                .isLessThan(friendship.getUserHigh().getId().toString());
        assertThat(friendship.getRequestedById()).isEqualTo(requester.getId());
        assertThat(friendship.sender()).isEqualTo(requester);
        assertThat(friendship.recipient()).isEqualTo(recipient);
        assertThat(friendship.isIncomingFor(recipient.getId())).isTrue();
        assertThat(friendship.isIncomingFor(requester.getId())).isFalse();
        assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.PENDING);
    }

    @Test
    void acceptsOnlyOnceAndRecordsTheAcceptanceTime() {
        UserAccount requester = user("AcceptOne", "accept-one@example.com");
        UserAccount recipient = user("AcceptTwo", "accept-two@example.com");
        Friendship friendship = Friendship.pending(requester, recipient, CREATED_AT);
        Instant acceptedAt = CREATED_AT.plusSeconds(30);

        friendship.accept(acceptedAt);

        assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
        assertThat(friendship.getAcceptedAt()).isEqualTo(acceptedAt);
        assertThatThrownBy(() -> friendship.accept(acceptedAt.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesSelfFriendship() {
        UserAccount user = user("SelfFriend", "self-friend@example.com");

        assertThatThrownBy(() -> Friendship.pending(user, user, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private UserAccount user(String username, String email) {
        return UserAccount.create(username, email, "not-used-by-domain-test", CREATED_AT);
    }
}
