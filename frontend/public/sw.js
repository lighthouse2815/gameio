const CACHE_VERSION = "gameio-shell-v1";
const OFFLINE_URL = "/offline";
const CORE_ASSETS = [
  OFFLINE_URL,
  "/manifest.webmanifest",
  "/icon.svg",
  "/icon-maskable.svg",
  "/game-art/2048.webp",
  "/game-art/snake.webp",
  "/game-art/flappy-bird.webp",
  "/game-art/breakout.svg",
  "/game-art/minesweeper.svg",
  "/game-art/memory-match.svg",
];

async function cacheOfflineBundle() {
  const cache = await caches.open(CACHE_VERSION);
  const response = await fetch(OFFLINE_URL, { cache: "reload" });
  if (!response.ok) throw new Error("Offline route could not be cached");
  await cache.put(OFFLINE_URL, response.clone());
  const html = await response.text();
  const assetUrls = [...html.matchAll(/(?:src|href)="([^"?]+\.(?:js|css|woff2))[^" ]*"/g)]
    .map((match) => match[1])
    .filter((url) => url.startsWith("/"));
  await Promise.allSettled([...new Set([...CORE_ASSETS.slice(1), ...assetUrls])].map((url) => cache.add(url)));
}

self.addEventListener("install", (event) => {
  event.waitUntil(cacheOfflineBundle().then(() => self.skipWaiting()));
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((key) => key !== CACHE_VERSION).map((key) => caches.delete(key))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener("fetch", (event) => {
  const request = event.request;
  if (request.method !== "GET") return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin || url.pathname.startsWith("/api/")) return;

  if (request.mode === "navigate") {
    event.respondWith(
      fetch(request)
        .then((response) => {
          const copy = response.clone();
          void caches.open(CACHE_VERSION).then((cache) => cache.put(request, copy));
          return response;
        })
        .catch(async () => (await caches.match(request)) || (await caches.match(OFFLINE_URL))),
    );
    return;
  }

  if (["script", "style", "font", "image"].includes(request.destination)) {
    event.respondWith(
      caches.match(request).then((cached) =>
        cached || fetch(request).then((response) => {
          if (response.ok) {
            const copy = response.clone();
            void caches.open(CACHE_VERSION).then((cache) => cache.put(request, copy));
          }
          return response;
        }),
      ),
    );
  }
});
