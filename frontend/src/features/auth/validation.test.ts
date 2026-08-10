import { describe, expect, it } from "vitest";
import {
  passwordUtf8ByteLength,
  validateLogin,
  validateRegister,
} from "@/features/auth/validation";

describe("authentication password validation", () => {
  it("measures BCrypt's limit in UTF-8 bytes", () => {
    expect(passwordUtf8ByteLength("a".repeat(72))).toBe(72);
    expect(passwordUtf8ByteLength("🔐".repeat(18))).toBe(72);
    expect(passwordUtf8ByteLength("🔐".repeat(19))).toBe(76);
  });

  it("accepts 72 bytes and rejects 73 bytes at login", () => {
    expect(
      validateLogin({ login: "player", password: "a".repeat(72) }).password,
    ).toBeUndefined();
    expect(
      validateLogin({ login: "player", password: "a".repeat(73) }).password,
    ).toBe("Use no more than 72 UTF-8 bytes.");
  });

  it("rejects a multibyte registration password beyond 72 bytes", () => {
    expect(
      validateRegister({
        username: "player_one",
        email: "player@example.com",
        password: "🔐".repeat(19),
      }).password,
    ).toBe("Use no more than 72 UTF-8 bytes.");
  });
});
