package com.gameio.gameresult.replay.game2048;

import java.util.List;

public record Game2048State(List<List<Integer>> board, long score, boolean gameOver, int highestValue) {
}
