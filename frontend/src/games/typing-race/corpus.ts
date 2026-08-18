export type TypingLesson = {
  id: string;
  title: string;
  description: string;
  prompt: string;
};

export const TYPING_LESSONS: readonly TypingLesson[] = [
  {
    id: "home-row",
    title: "Home row",
    description: "Anchor every finger, keep both wrists loose, and build a clean rhythm.",
    prompt: "asdf jkl; asdf jkl; sad lad; flask salad; all fall; ask dad;",
  },
  {
    id: "top-row",
    title: "Top row",
    description: "Reach upward without lifting the palms away from the home row.",
    prompt: "type quiet power with true rhythm; write every query with poise;",
  },
  {
    id: "bottom-row",
    title: "Bottom row",
    description: "Train controlled downward reaches and return each finger home.",
    prompt: "calm fingers move; mix clean rhythm; zoom back home; never cram;",
  },
  {
    id: "word-flow",
    title: "Word flow",
    description: "Link short words into one steady stream without chasing raw speed.",
    prompt: "Quick hands stay relaxed while accurate keystrokes build lasting speed.",
  },
  {
    id: "full-sprint",
    title: "Full sprint",
    description: "Hold your form through a complete race-length passage.",
    prompt: "Focus on accuracy first, then let smooth motion carry your runner toward the finish line.",
  },
];
