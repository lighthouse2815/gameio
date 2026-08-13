export type SessionUser = {
  id: string;
  username: string;
  email: string;
  avatarUrl?: string | null;
  level: number;
  exp: number;
};

export type AuthResponse = {
  tokenType: "Bearer" | string;
  accessToken: string;
  accessExpiresAt: string;
  user: SessionUser;
};

export type LoginInput = {
  login: string;
  password: string;
};

export type RegisterInput = {
  username: string;
  email: string;
  password: string;
};

export type GoogleLoginInput = {
  idToken: string;
};
