export type LevelProgress = {
  currentThreshold: number;
  nextThreshold: number;
  progressPercent: number;
};

export function experienceRequiredForLevel(level: number) {
  const normalizedLevel = Math.max(1, Math.trunc(level));
  return 100 * (normalizedLevel - 1) ** 2;
}

export function calculateLevelProgress(
  totalExperience: number,
  level: number,
): LevelProgress {
  const normalizedLevel = Math.max(1, Math.trunc(level));
  const currentThreshold = experienceRequiredForLevel(normalizedLevel);
  const nextThreshold = experienceRequiredForLevel(normalizedLevel + 1);
  const earnedInLevel = Math.max(0, totalExperience - currentThreshold);
  const levelSpan = Math.max(1, nextThreshold - currentThreshold);
  const progressPercent = Math.min(
    100,
    Math.max(0, Math.round((earnedInLevel / levelSpan) * 100)),
  );
  return { currentThreshold, nextThreshold, progressPercent };
}
