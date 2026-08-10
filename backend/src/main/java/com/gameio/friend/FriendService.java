package com.gameio.friend;

import com.gameio.user.UserAccount;
import com.gameio.user.UserNotFoundException;
import com.gameio.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FriendService {
    private static final Comparator<FriendRequestResponse> REQUEST_ORDER =
            Comparator.comparing(FriendRequestResponse::createdAt).reversed()
                    .thenComparing(FriendRequestResponse::id);

    private final FriendshipRepository friendships;
    private final UserRepository users;
    private final FriendPresenceReader presenceReader;
    private final Clock clock;

    public FriendService(
            FriendshipRepository friendships,
            UserRepository users,
            FriendPresenceReader presenceReader,
            Clock clock) {
        this.friendships = friendships;
        this.users = users;
        this.presenceReader = presenceReader;
        this.clock = clock;
    }

    @Transactional
    public FriendRequestResponse sendRequest(UUID requesterId, SendFriendRequest request) {
        UserAccount requester = users.findById(requesterId).orElseThrow(UserNotFoundException::new);
        UserAccount recipient = users.findByUsernameNormalized(UserAccount.normalize(request.username()))
                .orElseThrow(UserNotFoundException::new);
        if (requester.getId().equals(recipient.getId())) {
            throw InvalidFriendshipActionException.selfRequest();
        }

        Friendship.UserPair pair = Friendship.canonicalPair(requester.getId(), recipient.getId());
        friendships.findPair(pair.low(), pair.high()).ifPresent(this::rejectDuplicate);

        try {
            Friendship friendship = friendships.saveAndFlush(
                    Friendship.pending(requester, recipient, Instant.now(clock)));
            return FriendRequestResponse.from(friendship, readPresence(List.of(friendship)));
        } catch (DataIntegrityViolationException exception) {
            throw new FriendRequestAlreadyExistsException();
        }
    }

    @Transactional(readOnly = true)
    public List<FriendResponse> listFriends(UUID userId) {
        requireUser(userId);
        List<Friendship> accepted = friendships.findForUser(userId, FriendshipStatus.ACCEPTED);
        Map<UUID, FriendPresence> presenceByUser = readPresence(accepted);
        return accepted.stream()
                .map(friendship -> friendship.friendOf(userId))
                .map(friend -> FriendResponse.from(friend, presenceByUser.get(friend.getId())))
                .sorted(Comparator.comparing(FriendResponse::username, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(FriendResponse::id))
                .toList();
    }

    @Transactional(readOnly = true)
    public FriendRequestsResponse listRequests(UUID userId) {
        requireUser(userId);
        List<FriendRequestResponse> incoming = new ArrayList<>();
        List<FriendRequestResponse> outgoing = new ArrayList<>();
        List<Friendship> pending = friendships.findForUser(userId, FriendshipStatus.PENDING);
        Map<UUID, FriendPresence> presenceByUser = readPresence(pending);
        for (Friendship friendship : pending) {
            FriendRequestResponse response = FriendRequestResponse.from(friendship, presenceByUser);
            (friendship.isIncomingFor(userId) ? incoming : outgoing).add(response);
        }
        incoming.sort(REQUEST_ORDER);
        outgoing.sort(REQUEST_ORDER);
        return new FriendRequestsResponse(List.copyOf(incoming), List.copyOf(outgoing));
    }

    @Transactional
    public void accept(UUID userId, UUID requestId) {
        Friendship friendship = incomingPendingRequest(userId, requestId);
        friendship.accept(Instant.now(clock));
    }

    @Transactional
    public void reject(UUID userId, UUID requestId) {
        Friendship friendship = incomingPendingRequest(userId, requestId);
        friendships.delete(friendship);
    }

    @Transactional
    public void remove(UUID userId, String username) {
        requireUser(userId);
        UserAccount friend = users.findByUsernameNormalized(UserAccount.normalize(username))
                .orElseThrow(FriendshipNotFoundException::new);
        if (userId.equals(friend.getId())) {
            throw new FriendshipNotFoundException();
        }
        Friendship.UserPair pair = Friendship.canonicalPair(userId, friend.getId());
        Friendship friendship = friendships.findPair(pair.low(), pair.high())
                .filter(candidate -> candidate.getStatus() == FriendshipStatus.ACCEPTED)
                .orElseThrow(FriendshipNotFoundException::new);
        friendships.delete(friendship);
    }

    private Friendship incomingPendingRequest(UUID userId, UUID requestId) {
        Friendship friendship = friendships.findByIdForUpdate(requestId)
                .orElseThrow(FriendRequestNotFoundException::new);
        if (!friendship.contains(userId)) {
            throw new FriendRequestNotFoundException();
        }
        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw InvalidFriendshipActionException.notPending();
        }
        if (!friendship.isIncomingFor(userId)) {
            throw InvalidFriendshipActionException.notIncoming();
        }
        return friendship;
    }

    private void rejectDuplicate(Friendship friendship) {
        if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
            throw new AlreadyFriendsException();
        }
        throw new FriendRequestAlreadyExistsException();
    }

    private void requireUser(UUID userId) {
        if (!users.existsById(userId)) {
            throw new UserNotFoundException();
        }
    }

    private Map<UUID, FriendPresence> readPresence(List<Friendship> relationships) {
        Set<UUID> userIds = relationships.stream()
                .flatMap(friendship -> java.util.stream.Stream.of(
                        friendship.getUserLow().getId(), friendship.getUserHigh().getId()))
                .collect(Collectors.toUnmodifiableSet());
        return presenceReader.read(userIds);
    }
}
