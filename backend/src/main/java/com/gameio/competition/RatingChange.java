package com.gameio.competition;

public record RatingChange(int before, int after) {
    public int delta() { return after - before; }
}
