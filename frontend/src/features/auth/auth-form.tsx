"use client";

import { useState, type FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { useToast } from "@/components/ui/toast";
import { GoogleAuthButton } from "@/features/auth/google-auth-button";
import {
  useGoogleLogin,
  useLogin,
  useRegister,
} from "@/features/auth/hooks";
import type { LoginInput, RegisterInput } from "@/features/auth/types";
import {
  validateLogin,
  validateRegister,
} from "@/features/auth/validation";
import { getErrorMessage, isApiError } from "@/lib/api/api-error";

type AuthFormProps = {
  mode: "login" | "register";
};

const EMPTY_LOGIN: LoginInput = { login: "", password: "" };
const EMPTY_REGISTER: RegisterInput = {
  username: "",
  email: "",
  password: "",
};

export function AuthForm({ mode }: AuthFormProps) {
  const router = useRouter();
  const toast = useToast();
  const login = useLogin();
  const register = useRegister();
  const googleLogin = useGoogleLogin();
  const [loginInput, setLoginInput] = useState(EMPTY_LOGIN);
  const [registerInput, setRegisterInput] = useState(EMPTY_REGISTER);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [googleError, setGoogleError] = useState<string | null>(null);
  const mutation = mode === "login" ? login : register;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextErrors =
      mode === "login"
        ? validateLogin(loginInput)
        : validateRegister(registerInput);
    setErrors(nextErrors as Record<string, string>);
    if (Object.keys(nextErrors).length) {
      return;
    }
    try {
      if (mode === "login") {
        await login.mutateAsync(loginInput);
      } else {
        await register.mutateAsync(registerInput);
      }
      toast({
        title: mode === "login" ? "Link established" : "Identity created",
        description: "Your player session is active.",
        tone: "success",
      });
      router.replace("/");
    } catch (error) {
      const remoteErrors = isApiError(error) ? error.body?.fieldErrors : null;
      if (remoteErrors) {
        setErrors(remoteErrors);
      }
      toast({
        title: "Authentication failed",
        description: getErrorMessage(error),
        tone: "error",
      });
    }
  }

  async function submitWithGoogle(idToken: string) {
    setGoogleError(null);
    try {
      await googleLogin.mutateAsync({ idToken });
      toast({
        title: "Google link established",
        description: "Your player session is active.",
        tone: "success",
      });
      router.replace("/");
    } catch (error) {
      const description = getErrorMessage(error);
      setGoogleError(description);
      toast({
        title: "Google authentication failed",
        description,
        tone: "error",
      });
    }
  }

  return (
    <form className="grid gap-5" onSubmit={submit} noValidate>
      {mode === "register" ? (
        <>
          <Field
            label="Player call sign"
            name="username"
            autoComplete="username"
            value={registerInput.username}
            error={errors.username}
            disabled={googleLogin.isPending}
            onChange={(event) =>
              setRegisterInput((input) => ({
                ...input,
                username: event.target.value,
              }))
            }
          />
          <Field
            label="Email channel"
            name="email"
            type="email"
            autoComplete="email"
            value={registerInput.email}
            error={errors.email}
            disabled={googleLogin.isPending}
            onChange={(event) =>
              setRegisterInput((input) => ({
                ...input,
                email: event.target.value,
              }))
            }
          />
        </>
      ) : (
        <Field
          label="Call sign or email"
          name="login"
          autoComplete="username"
          value={loginInput.login}
          error={errors.login}
          disabled={googleLogin.isPending}
          onChange={(event) =>
            setLoginInput((input) => ({
              ...input,
              login: event.target.value,
            }))
          }
        />
      )}
      <Field
        label="Passphrase"
        name="password"
        type="password"
        autoComplete={mode === "login" ? "current-password" : "new-password"}
        value={mode === "login" ? loginInput.password : registerInput.password}
        error={errors.password}
        disabled={googleLogin.isPending}
        onChange={(event) => {
          const password = event.target.value;
          if (mode === "login") {
            setLoginInput((input) => ({ ...input, password }));
          } else {
            setRegisterInput((input) => ({ ...input, password }));
          }
        }}
      />
      <Button
        className="mt-2 w-full"
        type="submit"
        busy={mutation.isPending}
        disabled={googleLogin.isPending}
      >
        {mode === "login" ? "Enter Gameio" : "Create player"}
      </Button>
      <GoogleAuthButton
        mode={mode}
        busy={googleLogin.isPending}
        disabled={mutation.isPending}
        onCredential={(idToken) => void submitWithGoogle(idToken)}
      />
      {googleError ? (
        <p
          className="border-l-2 border-[var(--danger)] pl-3 text-xs leading-5 text-[var(--danger)]"
          role="alert"
        >
          {googleError}
        </p>
      ) : null}
      <p className="text-center text-xs leading-5 text-[var(--muted)]">
        {mode === "login" ? "No identity on file?" : "Already registered?"}{" "}
        <Link
          href={mode === "login" ? "/register" : "/login"}
          className="font-telemetry text-[10px] text-[var(--accent)] hover:underline"
        >
          {mode === "login" ? "Create one" : "Sign in"}
        </Link>
      </p>
    </form>
  );
}
