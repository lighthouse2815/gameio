package com.gameio.multiplayer.engine.dotsboxes;

import java.util.List;
import java.util.UUID;

public record DotsBoxesSnapshot(
        long sequence,
        List<List<Boolean>> horizontalEdges,
        List<List<Boolean>> verticalEdges,
        List<List<String>> boxes,
        List<Integer> scores,
        List<EdgeMove> legalMoves,
        EdgeMove lastEdge,
        UUID currentTurnPlayerId,
        UUID winnerId,
        boolean draw
) {
    public DotsBoxesSnapshot {
        horizontalEdges = horizontalEdges.stream().map(List::copyOf).toList();
        verticalEdges = verticalEdges.stream().map(List::copyOf).toList();
        boxes = boxes.stream().map(List::copyOf).toList();
        scores = List.copyOf(scores);
        legalMoves = List.copyOf(legalMoves);
    }
}
