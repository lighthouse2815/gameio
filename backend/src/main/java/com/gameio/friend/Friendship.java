package com.gameio.friend;

import com.gameio.user.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "friendships")
class Friendship {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_low_id", nullable = false)
    private UserAccount userLow;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_high_id", nullable = false)
    private UserAccount userHigh;

    @Column(name = "requested_by_id", nullable = false)
    private UUID requestedById;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FriendshipStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Version
    @Column(name = "entity_version", nullable = false)
    private long version;

    protected Friendship() {
    }

    private Friendship(
            UUID id,
            UserAccount userLow,
            UserAccount userHigh,
            UUID requestedById,
            Instant createdAt) {
        this.id = id;
        this.userLow = userLow;
        this.userHigh = userHigh;
        this.requestedById = requestedById;
        this.status = FriendshipStatus.PENDING;
        this.createdAt = createdAt;
    }

    static Friendship pending(UserAccount requester, UserAccount recipient, Instant now) {
        if (requester.getId().equals(recipient.getId())) {
            throw new IllegalArgumentException("A friendship requires two distinct users");
        }
        UserAccount low = requester;
        UserAccount high = recipient;
        if (compareIds(low.getId(), high.getId()) > 0) {
            low = recipient;
            high = requester;
        }
        return new Friendship(UUID.randomUUID(), low, high, requester.getId(), now);
    }

    static UserPair canonicalPair(UUID first, UUID second) {
        return compareIds(first, second) <= 0
                ? new UserPair(first, second)
                : new UserPair(second, first);
    }

    private static int compareIds(UUID first, UUID second) {
        return first.toString().compareTo(second.toString());
    }

    boolean contains(UUID userId) {
        return userLow.getId().equals(userId) || userHigh.getId().equals(userId);
    }

    boolean isIncomingFor(UUID userId) {
        return contains(userId) && !requestedById.equals(userId);
    }

    UserAccount sender() {
        return requestedById.equals(userLow.getId()) ? userLow : userHigh;
    }

    UserAccount recipient() {
        return requestedById.equals(userLow.getId()) ? userHigh : userLow;
    }

    UserAccount friendOf(UUID userId) {
        if (userLow.getId().equals(userId)) {
            return userHigh;
        }
        if (userHigh.getId().equals(userId)) {
            return userLow;
        }
        throw new IllegalArgumentException("User is not part of this friendship");
    }

    void accept(Instant now) {
        if (status != FriendshipStatus.PENDING) {
            throw new IllegalStateException("Only a pending request can be accepted");
        }
        status = FriendshipStatus.ACCEPTED;
        acceptedAt = now;
    }

    UUID getId() {
        return id;
    }

    UserAccount getUserLow() {
        return userLow;
    }

    UserAccount getUserHigh() {
        return userHigh;
    }

    UUID getRequestedById() {
        return requestedById;
    }

    FriendshipStatus getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getAcceptedAt() {
        return acceptedAt;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Friendship friendship && Objects.equals(id, friendship.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    record UserPair(UUID low, UUID high) {
    }
}
