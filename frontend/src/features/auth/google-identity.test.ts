import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  activateGoogleIdentity,
  loadGoogleIdentityServices,
  renderGoogleIdentityButton,
  resetGoogleIdentityForTests,
  type GoogleCredentialResponse,
  type GoogleIdentityApi,
} from "@/features/auth/google-identity";

function exposeGoogleApi(api: GoogleIdentityApi) {
  Object.defineProperty(window, "google", {
    configurable: true,
    value: { accounts: { id: api } },
  });
}

describe("Google Identity Services adapter", () => {
  beforeEach(() => {
    resetGoogleIdentityForTests();
    document.head.replaceChildren();
    Object.defineProperty(window, "google", {
      configurable: true,
      value: undefined,
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("memoizes one script load and resolves the installed API", async () => {
    const api: GoogleIdentityApi = {
      initialize: vi.fn(),
      renderButton: vi.fn(),
    };
    const first = loadGoogleIdentityServices();
    const second = loadGoogleIdentityServices();

    expect(second).toBe(first);
    const script = document.querySelector<HTMLScriptElement>(
      "#google-identity-services",
    );
    expect(script?.src).toBe("https://accounts.google.com/gsi/client");
    expect(script?.async).toBe(true);
    expect(script?.defer).toBe(true);

    exposeGoogleApi(api);
    script?.dispatchEvent(new Event("load"));
    await expect(first).resolves.toBe(api);
    expect(document.querySelectorAll("#google-identity-services")).toHaveLength(
      1,
    );
  });

  it("initializes once with FedCM and forwards credentials to the active surface", () => {
    let callback: ((response: GoogleCredentialResponse) => void) | undefined;
    const api: GoogleIdentityApi = {
      initialize: vi.fn((configuration) => {
        callback = configuration.callback;
      }),
      renderButton: vi.fn(),
    };
    const firstHandler = vi.fn();
    const secondHandler = vi.fn();

    activateGoogleIdentity(api, "client-id", firstHandler);
    activateGoogleIdentity(api, "client-id", secondHandler);
    callback?.({ credential: "google-id-token" });

    expect(api.initialize).toHaveBeenCalledTimes(1);
    expect(api.initialize).toHaveBeenCalledWith(
      expect.objectContaining({
        client_id: "client-id",
        use_fedcm_for_button: true,
      }),
    );
    expect(firstHandler).not.toHaveBeenCalled();
    expect(secondHandler).toHaveBeenCalledWith({
      credential: "google-id-token",
    });
  });

  it("renders the official button with the requested auth copy and width", () => {
    const api: GoogleIdentityApi = {
      initialize: vi.fn(),
      renderButton: vi.fn(),
    };
    const parent = document.createElement("div");
    parent.append(document.createElement("span"));

    renderGoogleIdentityButton(api, parent, "signup_with", 360);

    expect(parent.childElementCount).toBe(0);
    expect(api.renderButton).toHaveBeenCalledWith(parent, {
      type: "standard",
      theme: "outline",
      size: "large",
      text: "signup_with",
      shape: "rectangular",
      logo_alignment: "left",
      width: 360,
    });
  });

  it("rejects a failed script request and allows a later retry", async () => {
    const first = loadGoogleIdentityServices();
    const failedScript = document.querySelector<HTMLScriptElement>(
      "#google-identity-services",
    );
    failedScript?.dispatchEvent(new Event("error"));
    await expect(first).rejects.toThrow(
      "Google Identity Services could not be loaded.",
    );
    expect(failedScript?.isConnected).toBe(false);

    const retry = loadGoogleIdentityServices();
    expect(retry).not.toBe(first);
    const retryScript = document.querySelector<HTMLScriptElement>(
      "#google-identity-services",
    );
    expect(retryScript).not.toBe(failedScript);

    const api: GoogleIdentityApi = {
      initialize: vi.fn(),
      renderButton: vi.fn(),
    };
    exposeGoogleApi(api);
    retryScript?.dispatchEvent(new Event("load"));
    await expect(retry).resolves.toBe(api);
  });

  it("removes a loaded script that did not install the GIS API", async () => {
    const request = loadGoogleIdentityServices();
    const script = document.querySelector<HTMLScriptElement>(
      "#google-identity-services",
    );
    script?.dispatchEvent(new Event("load"));

    await expect(request).rejects.toThrow(
      "Google Identity Services loaded without an API.",
    );
    expect(script?.isConnected).toBe(false);
  });
});
