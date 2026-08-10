package com.gameio.gameresult.replay;

import java.util.List;

public interface GameReplayVerifier {
    String gameSlug();

    Object initialState(long seed);

    VerifiedReplay verify(long seed, List<String> actions);
}
