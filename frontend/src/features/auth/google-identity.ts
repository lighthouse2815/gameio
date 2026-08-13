const GOOGLE_IDENTITY_SCRIPT_ID = "google-identity-services";
const GOOGLE_IDENTITY_SCRIPT_SRC = "https://accounts.google.com/gsi/client";

export type GoogleCredentialResponse = {
  credential?: string;
  select_by?: string;
};

export type GoogleButtonText = "signin_with" | "signup_with";

export type GoogleIdentityApi = {
  initialize: (configuration: {
    client_id: string;
    callback: (response: GoogleCredentialResponse) => void;
    use_fedcm_for_button: boolean;
  }) => void;
  renderButton: (
    parent: HTMLElement,
    options: {
      type: "standard";
      theme: "outline";
      size: "large";
      text: GoogleButtonText;
      shape: "rectangular";
      logo_alignment: "left";
      width: number;
      locale?: string;
    },
  ) => void;
};

type GoogleIdentityWindow = Window & {
  google?: {
    accounts?: {
      id?: GoogleIdentityApi;
    };
  };
};

let scriptRequest: Promise<GoogleIdentityApi> | null = null;
let initializedClientId: string | null = null;
let activeCredentialHandler:
  | ((response: GoogleCredentialResponse) => void)
  | null = null;

export function resetGoogleIdentityForTests() {
  scriptRequest = null;
  initializedClientId = null;
  activeCredentialHandler = null;
}

function availableApi() {
  return (window as GoogleIdentityWindow).google?.accounts?.id ?? null;
}

export function loadGoogleIdentityServices() {
  const readyApi = availableApi();
  if (readyApi) return Promise.resolve(readyApi);
  if (scriptRequest) return scriptRequest;

  const request = new Promise<GoogleIdentityApi>((resolve, reject) => {
    const existing = document.getElementById(
      GOOGLE_IDENTITY_SCRIPT_ID,
    ) as HTMLScriptElement | null;
    const script = existing ?? document.createElement("script");

    const cleanUp = () => {
      script.removeEventListener("load", handleLoad);
      script.removeEventListener("error", handleError);
    };
    const handleLoad = () => {
      cleanUp();
      const api = availableApi();
      if (api) {
        script.dataset.gameioLoaded = "true";
        resolve(api);
      } else {
        script.remove();
        reject(new Error("Google Identity Services loaded without an API."));
      }
    };
    const handleError = () => {
      cleanUp();
      script.remove();
      reject(new Error("Google Identity Services could not be loaded."));
    };

    script.addEventListener("load", handleLoad, { once: true });
    script.addEventListener("error", handleError, { once: true });

    if (existing?.dataset.gameioLoaded === "true") {
      queueMicrotask(handleLoad);
      return;
    }
    if (!existing) {
      script.id = GOOGLE_IDENTITY_SCRIPT_ID;
      script.src = GOOGLE_IDENTITY_SCRIPT_SRC;
      script.async = true;
      script.defer = true;
      document.head.appendChild(script);
    }
  });

  scriptRequest = request;
  void request.catch(() => {
    if (scriptRequest === request) scriptRequest = null;
  });
  return request;
}

export function activateGoogleIdentity(
  api: GoogleIdentityApi,
  clientId: string,
  handler: (response: GoogleCredentialResponse) => void,
) {
  if (initializedClientId && initializedClientId !== clientId) {
    throw new Error(
      "Google Identity Services is already initialized for another client.",
    );
  }
  if (!initializedClientId) {
    api.initialize({
      client_id: clientId,
      use_fedcm_for_button: true,
      callback: (response) => activeCredentialHandler?.(response),
    });
    initializedClientId = clientId;
  }
  activeCredentialHandler = handler;
  return () => {
    if (activeCredentialHandler === handler) activeCredentialHandler = null;
  };
}

export function renderGoogleIdentityButton(
  api: GoogleIdentityApi,
  parent: HTMLElement,
  text: GoogleButtonText,
  width: number,
  locale?: string,
) {
  parent.replaceChildren();
  api.renderButton(parent, {
    type: "standard",
    theme: "outline",
    size: "large",
    text,
    shape: "rectangular",
    logo_alignment: "left",
    width,
    locale,
  });
}
