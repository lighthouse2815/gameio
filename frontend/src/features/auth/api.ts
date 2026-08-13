import { apiClient } from "@/lib/api/client";
import type {
  AuthResponse,
  GoogleLoginInput,
  LoginInput,
  RegisterInput,
  SessionUser,
} from "@/features/auth/types";

export const authApi = {
  login: (input: LoginInput) =>
    apiClient.post<AuthResponse>("/auth/login", input, { auth: false }),
  register: (input: RegisterInput) =>
    apiClient.post<AuthResponse>("/auth/register", input, { auth: false }),
  google: (input: GoogleLoginInput) =>
    apiClient.post<AuthResponse>("/auth/google", input, { auth: false }),
  logout: () =>
    apiClient.post<void>("/auth/logout", undefined, { auth: false }),
  me: () => apiClient.get<SessionUser>("/users/me"),
};
