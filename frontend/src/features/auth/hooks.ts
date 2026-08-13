"use client";

import {
  useMutation,
  useQuery,
  useQueryClient,
  type QueryClient,
} from "@tanstack/react-query";
import { authApi } from "@/features/auth/api";
import type {
  AuthResponse,
  GoogleLoginInput,
  LoginInput,
  RegisterInput,
} from "@/features/auth/types";
import { isApiError } from "@/lib/api/api-error";
import { tokenVault } from "@/lib/api/token-vault";
import { gameSocketClient } from "@/lib/socket/game-socket-client";

export const sessionQueryKey = ["session"] as const;

export function applyAuthSession(
  queryClient: QueryClient,
  response: AuthResponse,
) {
  tokenVault.setAccessToken(response.accessToken);
  queryClient.setQueryData(sessionQueryKey, response.user);
}

function useAuthSessionMutation<Input>(
  mutationFn: (input: Input) => Promise<AuthResponse>,
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: (response) => applyAuthSession(queryClient, response),
  });
}

export function useSession() {
  return useQuery({
    queryKey: sessionQueryKey,
    queryFn: authApi.me,
    retry: false,
    staleTime: 60_000,
  });
}

export function useLogin() {
  return useAuthSessionMutation<LoginInput>(authApi.login);
}

export function useRegister() {
  return useAuthSessionMutation<RegisterInput>(authApi.register);
}

export function useGoogleLogin() {
  return useAuthSessionMutation<GoogleLoginInput>(authApi.google);
}

export function useLogout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: authApi.logout,
    onSettled: () => {
      tokenVault.setAccessToken(null);
      gameSocketClient.disconnect();
      queryClient.clear();
      queryClient.setQueryData(sessionQueryKey, null);
    },
  });
}

export function isUnauthenticated(error: unknown) {
  return isApiError(error) && error.status === 401;
}
