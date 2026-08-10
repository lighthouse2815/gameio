package com.gameio.gameresult;

import java.util.UUID;

public record GamePlayCount(UUID gameId, long playsCount) {
}
