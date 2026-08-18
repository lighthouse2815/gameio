export type PracticePhase = "ready" | "countdown" | "playing" | "complete";

export type TypingPracticeState = {
  phase: PracticePhase;
  prompt: string;
  graphemes: string[];
  cursor: number;
  attempts: number;
  correctCharacters: number;
  mistakes: number;
  combo: number;
  bestCombo: number;
  startedAtMs: number | null;
  finishedAtMs: number | null;
};

export type TypingMetrics = {
  progressPercent: number;
  wpm: number;
  accuracyPercent: number;
  elapsedMs: number;
};

export function toGraphemes(value: string) {
  const normalized = value.normalize("NFC");
  if (typeof Intl !== "undefined" && "Segmenter" in Intl) {
    const segmenter = new Intl.Segmenter(undefined, { granularity: "grapheme" });
    return Array.from(segmenter.segment(normalized), (entry) => entry.segment);
  }
  return Array.from(normalized);
}

export function createTypingPractice(prompt: string): TypingPracticeState {
  const normalized = prompt.normalize("NFC");
  const graphemes = toGraphemes(normalized);
  if (!graphemes.length || graphemes.length > 512) {
    throw new Error("Typing practice prompt must contain between 1 and 512 characters.");
  }
  return {
    phase: "ready",
    prompt: normalized,
    graphemes,
    cursor: 0,
    attempts: 0,
    correctCharacters: 0,
    mistakes: 0,
    combo: 0,
    bestCombo: 0,
    startedAtMs: null,
    finishedAtMs: null,
  };
}

export function beginTypingPractice(state: TypingPracticeState, nowMs: number) {
  return { ...state, phase: "playing" as const, startedAtMs: nowMs };
}

export function applyPracticeCharacter(
  state: TypingPracticeState,
  value: string,
  nowMs: number,
) {
  if (state.phase !== "playing") {
    return { state, correct: false, changed: false };
  }
  const graphemes = toGraphemes(value);
  if (graphemes.length !== 1) {
    return { state, correct: false, changed: false };
  }
  const correct = graphemes[0] === state.graphemes[state.cursor];
  const cursor = state.cursor + (correct ? 1 : 0);
  const combo = correct ? state.combo + 1 : 0;
  const complete = cursor === state.graphemes.length;
  const next: TypingPracticeState = {
    ...state,
    phase: complete ? "complete" : "playing",
    cursor,
    attempts: state.attempts + 1,
    correctCharacters: state.correctCharacters + (correct ? 1 : 0),
    mistakes: state.mistakes + (correct ? 0 : 1),
    combo,
    bestCombo: Math.max(state.bestCombo, combo),
    finishedAtMs: complete ? nowMs : null,
  };
  return { state: next, correct, changed: true };
}

export function typingMetrics(state: TypingPracticeState, nowMs: number): TypingMetrics {
  const measuredAt = state.finishedAtMs ?? nowMs;
  const elapsedMs = state.startedAtMs === null ? 0 : Math.max(0, measuredAt - state.startedAtMs);
  const safeElapsed = Math.max(1_000, elapsedMs);
  const attempts = state.correctCharacters + state.mistakes;
  return {
    progressPercent: Math.round((state.cursor / state.graphemes.length) * 100),
    wpm: state.correctCharacters
      ? Math.min(2_000, Math.floor((state.correctCharacters * 12_000) / safeElapsed))
      : 0,
    accuracyPercent: attempts
      ? Math.round((state.correctCharacters / attempts) * 100)
      : 100,
    elapsedMs,
  };
}
