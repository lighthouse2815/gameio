package com.gameio.multiplayer.engine.typingrace;

public record TypingPassage(String id, String text) {
    public TypingPassage {
        if (id == null || id.isBlank() || id.length() > 64
                || text == null || text.isBlank() || text.codePointCount(0, text.length()) > 512) {
            throw new IllegalArgumentException("Typing passage is invalid");
        }
    }
}
