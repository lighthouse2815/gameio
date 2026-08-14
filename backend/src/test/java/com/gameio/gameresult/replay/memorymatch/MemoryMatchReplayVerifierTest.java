package com.gameio.gameresult.replay.memorymatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gameio.common.error.InvalidGameActionException;
import com.gameio.gameresult.replay.SeededRandom;
import com.gameio.gameresult.replay.VerifiedReplay;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MemoryMatchReplayVerifierTest {
    private final MemoryMatchReplayVerifier verifier = new MemoryMatchReplayVerifier();

    @Test
    void startsWithEveryCardHidden() {
        MemoryState state = verifier.initialState(42);
        assertThat(state.cells()).hasSize(16).allSatisfy(cell -> {
            assertThat(cell.revealed()).isFalse();
            assertThat(cell.value()).isNull();
        });
    }

    @Test
    void verifiesACompleteSeededPairReplay() {
        long seed = 7_936;
        int[] deck = shuffledDeck(seed);
        Map<Integer, List<Integer>> positions = new HashMap<>();
        for (int index = 0; index < deck.length; index++) {
            positions.computeIfAbsent(deck[index], ignored -> new ArrayList<>()).add(index);
        }
        List<String> actions = new ArrayList<>();
        positions.values().stream().sorted((left, right) -> Integer.compare(left.get(0), right.get(0)))
                .forEach(pair -> {
                    actions.add("S:" + pair.get(0));
                    actions.add("S:" + pair.get(1));
                });

        VerifiedReplay replay = verifier.verify(seed, actions);
        assertThat(replay.gameOver()).isTrue();
        assertThat(replay.score()).isPositive();
        assertThat(((MemoryState) replay.finalState()).matchedPairs()).isEqualTo(8);
    }

    @Test
    void rejectsSelectingTheSameCardTwice() {
        assertThatThrownBy(() -> verifier.verify(1, List.of("S:0", "S:0")))
                .isInstanceOf(InvalidGameActionException.class);
    }

    private int[] shuffledDeck(long seed) {
        int[] deck = new int[16];
        for (int value = 0; value < 8; value++) {
            deck[value * 2] = value;
            deck[value * 2 + 1] = value;
        }
        SeededRandom random = new SeededRandom(seed);
        for (int index = deck.length - 1; index > 0; index--) {
            int swapIndex = random.nextIndex(index + 1);
            int value = deck[index];
            deck[index] = deck[swapIndex];
            deck[swapIndex] = value;
        }
        return deck;
    }
}
