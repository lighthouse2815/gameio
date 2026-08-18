package com.gameio.multiplayer.engine.typingrace;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class TypingPassageCatalog {
    private static final List<TypingPassage> PASSAGES = List.of(
            new TypingPassage("home-row-01",
                    "Steady hands stay relaxed while each clean keystroke builds speed and control."),
            new TypingPassage("quick-flow-01",
                    "Quick fingers move with calm rhythm as bright signals race across the keyboard grid."),
            new TypingPassage("focus-run-01",
                    "Focus on accuracy first, then let smooth motion carry your runner toward the finish."),
            new TypingPassage("momentum-01",
                    "Small precise movements become real momentum when every finger returns to the home row."));

    private final AtomicInteger nextIndex = new AtomicInteger();

    public TypingPassage next() {
        return PASSAGES.get(Math.floorMod(nextIndex.getAndIncrement(), PASSAGES.size()));
    }

    public List<TypingPassage> all() {
        return PASSAGES;
    }
}
