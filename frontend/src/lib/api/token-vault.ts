import {
  publishAccessToken,
  publishLogout,
  subscribeAuthSync,
} from "@/lib/api/auth-sync";

export type AuthChangeReason = "token" | "logout";

class TokenVault {
  private accessToken: string | null = null;

  constructor() {
    if (typeof window !== "undefined") {
      subscribeAuthSync((event) => {
        if (event.type === "access-token") {
          this.setAccessToken(event.token, false);
        } else {
          this.setAccessToken(null, false);
        }
      });
    }
  }

  getAccessToken() {
    return this.accessToken;
  }

  setAccessToken(token: string | null, broadcast = true) {
    this.accessToken = token;
    if (typeof window !== "undefined") {
      if (broadcast) {
        if (token) publishAccessToken(token);
        else publishLogout();
      }
      window.dispatchEvent(
        new CustomEvent<{ reason: AuthChangeReason }>("gameio:auth-change", {
          detail: { reason: token ? "token" : "logout" },
        }),
      );
    }
  }
}

export const tokenVault = new TokenVault();
