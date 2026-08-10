package com.gameio.matchmaking;

import com.gameio.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/matchmaking")
public class MatchmakingController {
    private final MatchmakingService matchmaking;
    private final CurrentUser currentUser;

    public MatchmakingController(MatchmakingService matchmaking, CurrentUser currentUser) {
        this.matchmaking = matchmaking;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    MatchmakingTicketResponse join(Authentication authentication, @Valid @RequestBody JoinMatchmakingRequest request) {
        return matchmaking.join(currentUser.id(authentication), request.gameId());
    }

    @GetMapping
    MatchmakingTicketResponse current(Authentication authentication) {
        return matchmaking.current(currentUser.id(authentication));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void leave(Authentication authentication) {
        matchmaking.leave(currentUser.id(authentication));
    }
}
