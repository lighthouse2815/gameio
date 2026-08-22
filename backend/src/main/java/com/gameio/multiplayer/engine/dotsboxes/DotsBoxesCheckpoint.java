package com.gameio.multiplayer.engine.dotsboxes;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record DotsBoxesCheckpoint(
        int version,
        long sequence,
        List<List<Boolean>> horizontalEdges,
        List<List<Boolean>> verticalEdges,
        List<List<Integer>> boxes,
        List<Integer> scores,
        int currentPlayer,
        EdgeMove lastEdge,
        UUID winnerId,
        boolean draw
) {
    public static final int CURRENT_VERSION = 1;

    public DotsBoxesCheckpoint {
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Dots and Boxes checkpoint version");
        }
        Objects.requireNonNull(horizontalEdges);
        Objects.requireNonNull(verticalEdges);
        Objects.requireNonNull(boxes);
        Objects.requireNonNull(scores);
        horizontalEdges = horizontalEdges.stream().map(List::copyOf).toList();
        verticalEdges = verticalEdges.stream().map(List::copyOf).toList();
        boxes = boxes.stream().map(List::copyOf).toList();
        scores = List.copyOf(scores);
    }
}
