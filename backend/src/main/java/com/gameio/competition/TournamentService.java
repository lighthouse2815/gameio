package com.gameio.competition;

import com.gameio.common.error.ConflictException;
import com.gameio.common.web.PageResponse;
import com.gameio.game.Game;
import com.gameio.game.GameNotFoundException;
import com.gameio.game.GameRepository;
import com.gameio.game.GameType;
import com.gameio.gameresult.GameResultType;
import com.gameio.gameresult.multiplayer.AuthoritativePlayerOutcome;
import com.gameio.room.InvalidRoomActionException;
import com.gameio.room.RoomService;
import com.gameio.room.RoomState;
import com.gameio.user.UserAccount;
import com.gameio.user.UserNotFoundException;
import com.gameio.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TournamentService {
    private final TournamentRepository tournaments;
    private final TournamentEntryRepository entries;
    private final TournamentMatchRepository matches;
    private final GameRepository games;
    private final UserRepository users;
    private final RoomService rooms;
    private final Clock clock;

    TournamentService(
            TournamentRepository tournaments,
            TournamentEntryRepository entries,
            TournamentMatchRepository matches,
            GameRepository games,
            UserRepository users,
            RoomService rooms,
            Clock clock) {
        this.tournaments = tournaments;
        this.entries = entries;
        this.matches = matches;
        this.games = games;
        this.users = users;
        this.rooms = rooms;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<TournamentSummaryResponse> list(int page, int size) {
        Page<Tournament> result = tournaments.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
        List<TournamentSummaryResponse> content = result.getContent().stream()
                .map(tournament -> TournamentSummaryResponse.from(
                        tournament, entries.countByTournamentId(tournament.id())))
                .toList();
        return new PageResponse<>(content, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public TournamentDetailResponse detail(UUID tournamentId) {
        Tournament tournament = tournaments.findDetailedById(tournamentId)
                .orElseThrow(() -> new InvalidRoomActionException("TOURNAMENT_NOT_FOUND", "Tournament was not found"));
        List<TournamentPlayerResponse> players = entries.findByTournamentIdOrderBySeedNumberAsc(tournamentId)
                .stream().map(TournamentPlayerResponse::from).toList();
        List<TournamentMatchResponse> bracket = matches
                .findByTournamentIdOrderByRoundNumberAscBracketIndexAsc(tournamentId)
                .stream().map(TournamentMatchResponse::from).toList();
        return new TournamentDetailResponse(TournamentSummaryResponse.from(tournament, players.size()),
                players, bracket);
    }

    @Transactional
    public TournamentDetailResponse create(UUID userId, CreateTournamentRequest request) {
        Game game = games.findById(request.gameId()).filter(Game::isEnabled)
                .filter(candidate -> candidate.getGameType() != GameType.SINGLE_PLAYER)
                .orElseThrow(GameNotFoundException::new);
        UserAccount creator = requireUser(userId);
        Instant now = Instant.now(clock);
        Tournament tournament = tournaments.save(Tournament.create(
                request.name().trim(), game, creator, request.parsedMaxPlayers(), now));
        entries.save(TournamentEntry.create(tournament, creator, 1, now));
        return detail(tournament.id());
    }

    @Transactional
    public TournamentDetailResponse join(UUID userId, UUID tournamentId) {
        Tournament tournament = requireForUpdate(tournamentId);
        if (tournament.status() != TournamentStatus.REGISTRATION) {
            throw new ConflictException("TOURNAMENT_ALREADY_STARTED", "Tournament registration is closed");
        }
        if (entries.existsByTournamentIdAndUserId(tournamentId, userId)) return detail(tournamentId);
        long playerCount = entries.countByTournamentId(tournamentId);
        if (playerCount >= tournament.maxPlayers()) {
            throw new ConflictException("TOURNAMENT_FULL", "Tournament has reached its player limit");
        }
        entries.save(TournamentEntry.create(tournament, requireUser(userId), Math.toIntExact(playerCount + 1),
                Instant.now(clock)));
        return detail(tournamentId);
    }

    @Transactional
    public TournamentDetailResponse start(UUID userId, UUID tournamentId) {
        Tournament tournament = requireForUpdate(tournamentId);
        if (!tournament.createdBy().getId().equals(userId)) {
            throw new InvalidRoomActionException("TOURNAMENT_OWNER_REQUIRED",
                    "Only the tournament creator can start the bracket");
        }
        if (tournament.status() != TournamentStatus.REGISTRATION) {
            throw new ConflictException("TOURNAMENT_ALREADY_STARTED", "Tournament has already started");
        }
        List<UserAccount> players = entries.findByTournamentIdOrderBySeedNumberAsc(tournamentId)
                .stream().map(TournamentEntry::user).toList();
        if (players.size() < 2) {
            throw new InvalidRoomActionException("TOURNAMENT_NOT_READY",
                    "At least two players are required to start a tournament");
        }
        createRound(tournament, players, Instant.now(clock));
        return detail(tournamentId);
    }

    @Transactional
    public void recordMatchResult(UUID roomId, List<AuthoritativePlayerOutcome> outcomes) {
        TournamentMatch match = matches.findByRoomId(roomId).orElse(null);
        if (match == null || match.status() == TournamentMatchStatus.COMPLETED) return;
        Tournament tournament = requireForUpdate(match.tournament().id());
        AuthoritativePlayerOutcome winnerOutcome = outcomes.stream()
                .filter(outcome -> outcome.result() == GameResultType.WIN)
                .findFirst().orElse(null);
        Instant now = Instant.now(clock);
        if (winnerOutcome == null) {
            RoomState rematch = rooms.createForMatchmaking(tournament.game().getId(),
                    List.of(match.playerOne().getId(), match.playerTwo().getId()));
            match.replaceRoom(rematch.roomId());
            matches.save(match);
            return;
        }
        UserAccount winner = participant(match, winnerOutcome.userId());
        UserAccount loser = match.playerOne().getId().equals(winner.getId()) ? match.playerTwo() : match.playerOne();
        match.complete(winner, now);
        matches.saveAndFlush(match);
        entries.findByTournamentIdAndUserId(tournament.id(), loser.getId()).ifPresent(TournamentEntry::eliminate);

        List<TournamentMatch> round = matches.findByTournamentIdAndRoundNumberOrderByBracketIndexAsc(
                tournament.id(), tournament.currentRound());
        if (round.stream().anyMatch(candidate -> candidate.status() != TournamentMatchStatus.COMPLETED)) return;
        List<UserAccount> winners = round.stream().map(TournamentMatch::winner).toList();
        if (winners.size() == 1) {
            tournament.complete(winners.getFirst(), now);
            return;
        }
        createRound(tournament, winners, now);
    }

    private void createRound(Tournament tournament, List<UserAccount> players, Instant now) {
        int roundNumber = tournament.currentRound() + 1;
        tournament.startRound(roundNumber, now);
        List<TournamentMatch> created = new ArrayList<>();
        for (int index = 0; index < players.size(); index += 2) {
            UserAccount first = players.get(index);
            UserAccount second = index + 1 < players.size() ? players.get(index + 1) : null;
            int bracketIndex = index / 2;
            if (second == null) {
                created.add(TournamentMatch.bye(tournament, roundNumber, bracketIndex, first, now));
            } else {
                RoomState room = rooms.createForMatchmaking(tournament.game().getId(),
                        List.of(first.getId(), second.getId()));
                created.add(TournamentMatch.active(tournament, roundNumber, bracketIndex,
                        first, second, room.roomId(), now));
            }
        }
        matches.saveAll(created);
    }

    private Tournament requireForUpdate(UUID tournamentId) {
        return tournaments.findForUpdateById(tournamentId)
                .orElseThrow(() -> new InvalidRoomActionException("TOURNAMENT_NOT_FOUND", "Tournament was not found"));
    }

    private UserAccount requireUser(UUID userId) {
        return users.findById(userId).orElseThrow(UserNotFoundException::new);
    }

    private UserAccount participant(TournamentMatch match, UUID userId) {
        if (match.playerOne().getId().equals(userId)) return match.playerOne();
        if (match.playerTwo() != null && match.playerTwo().getId().equals(userId)) return match.playerTwo();
        throw new InvalidRoomActionException("TOURNAMENT_RESULT_INVALID",
                "Tournament result contains a player outside the bracket match");
    }
}
