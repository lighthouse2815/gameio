export class SeededRandom {
  private state: number;

  constructor(seed: number) {
    this.state = (seed >>> 0) || 0x6d2b79f5;
  }

  nextIndex(bound: number) {
    if (!Number.isInteger(bound) || bound <= 0) {
      throw new Error("Bound must be a positive integer");
    }
    let value = this.state;
    value ^= value << 13;
    value ^= value >>> 17;
    value ^= value << 5;
    this.state = value >>> 0;
    return Math.floor((this.state * bound) / 4_294_967_296);
  }
}

export function randomSeed() {
  const values = new Uint32Array(1);
  window.crypto.getRandomValues(values);
  return values[0] || 1;
}
