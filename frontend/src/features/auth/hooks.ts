"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { authApi } from "@/features/auth/api";
import type { LoginInput, RegisterInput } from "@/features/auth/types";
import { isApiError } from "@/lib/api/api-error";
import { tokenVault } from "@/lib/api/token-vault";
import { gameSocketClient } from "@/lib/socket/game-socket-client";

export const sessionQueryKey = ["session"] as const;

export function useSession() {
  return useQuery({
    queryKey: sessionQueryKey,
    queryFn: authApi.me,
    retry: false,
    staleTime: 60_000,
  });
}

export function useLogin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: LoginInput) => authApi.login(input),
    onSuccess: (response) => {
      tokenVault.setAccessToken(response.accessToken);
      queryClient.setQueryData(sessionQueryKey, response.user);
    },
  });
}

export function useRegister() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: RegisterInput) => authApi.register(input),
    onSuccess: (response) => {
      tokenVault.setAccessToken(response.accessToken);
      queryClient.setQueryData(sessionQueryKey, response.user);
    },
  });
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
