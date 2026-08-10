export {};

declare global {
  interface CloudflareEnv {
    BACKEND_ORIGIN: string;
  }
}
