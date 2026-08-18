import { describe, expect, it } from "vitest";
import {
  applyPracticeCharacter,
  beginTypingPractice,
  createTypingPractice,
  toGraphemes,
  typingMetrics,
  type TypingPracticeState,
} from "@/games/typing-race/engine";

describe("typing practice engine", () => {
  it("advances only correct characters and resets combo on mistakes", () => {
    let state: TypingPracticeState = beginTypingPractice(createTypingPractice("ab"), 1_000);
    const first = applyPracticeCharacter(state, "a", 2_000);
    state = first.state;
    expect(first.correct).toBe(true);
    expect(state.cursor).toBe(1);
    expect(state.combo).toBe(1);

    state = applyPracticeCharacter(state, "x", 2_500).state;
    expect(state.cursor).toBe(1);
    expect(state.mistakes).toBe(1);
    expect(state.combo).toBe(0);

    state = applyPracticeCharacter(state, "b", 3_000).state;
    expect(state.phase).toBe("complete");
    expect(state.correctCharacters).toBe(2);
    expect(state.bestCombo).toBe(1);
  });

  it("calculates finite WPM, accuracy, and progress from explicit time", () => {
    let state: TypingPracticeState = beginTypingPractice(createTypingPractice("abcde"), 0);
    state = applyPracticeCharacter(state, "a", 1_000).state;
    state = applyPracticeCharacter(state, "x", 2_000).state;
    state = applyPracticeCharacter(state, "b", 3_000).state;
    const metrics = typingMetrics(state, 12_000);
    expect(metrics.wpm).toBe(2);
    expect(metrics.accuracyPercent).toBe(67);
    expect(metrics.progressPercent).toBe(40);
    expect(Number.isFinite(metrics.wpm)).toBe(true);
  });

  it("normalizes composed characters and preserves grapheme clusters", () => {
    expect(toGraphemes("a\u0301")).toEqual(["á"]);
    expect(toGraphemes("⌨️")).toEqual(["⌨️"]);
  });

  it("ignores input outside the active lifecycle", () => {
    const ready = createTypingPractice("abc");
    expect(applyPracticeCharacter(ready, "a", 100).changed).toBe(false);
  });
});
