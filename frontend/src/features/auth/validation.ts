import type { LoginInput, RegisterInput } from "@/features/auth/types";
import { translate } from "@/lib/i18n/messages";
import type { Locale } from "@/lib/i18n/types";

export type FieldErrors<T> = Partial<Record<keyof T, string>>;

export function passwordUtf8ByteLength(password: string) {
  return new TextEncoder().encode(password).byteLength;
}

export function validateLogin(
  input: LoginInput,
  locale: Locale = "en",
): FieldErrors<LoginInput> {
  const errors: FieldErrors<LoginInput> = {};
  if (!input.login.trim()) {
    errors.login = translate(locale, "Enter your username or email.");
  }
  if (!input.password) {
    errors.password = translate(locale, "Enter your password.");
  } else if (passwordUtf8ByteLength(input.password) > 72) {
    errors.password = translate(locale, "Use no more than 72 UTF-8 bytes.");
  }
  return errors;
}

export function validateRegister(
  input: RegisterInput,
  locale: Locale = "en",
): FieldErrors<RegisterInput> {
  const errors: FieldErrors<RegisterInput> = {};
  if (!/^[a-zA-Z0-9_]{3,24}$/.test(input.username)) {
    errors.username = translate(locale, "Use 3–24 letters, numbers, or underscores.");
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(input.email)) {
    errors.email = translate(locale, "Enter a valid email address.");
  }
  if (input.password.length < 10) {
    errors.password = translate(locale, "Use at least 10 characters.");
  } else if (passwordUtf8ByteLength(input.password) > 72) {
    errors.password = translate(locale, "Use no more than 72 UTF-8 bytes.");
  }
  return errors;
}
