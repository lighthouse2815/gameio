import type { Metadata, Viewport } from "next";
import "@fontsource-variable/inter";
import "@fontsource-variable/jetbrains-mono";
import "@/app/globals.css";
import { SiteFooter } from "@/components/layout/site-footer";
import { SiteHeader } from "@/components/layout/site-header";
import { Providers } from "@/app/providers";

export const metadata: Metadata = {
  title: {
    default: "Gameio — Play Network",
    template: "%s — Gameio",
  },
  description:
    "A precision-built mini-game portal for solo runs, multiplayer rooms, and verified rankings.",
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  themeColor: [
    { media: "(prefers-color-scheme: dark)", color: "#0a0a0a" },
    { media: "(prefers-color-scheme: light)", color: "#efede7" },
  ],
};

const themeScript =
  "(function(){try{var m=localStorage.getItem('gameio.theme');var t=m==='light'?'light':'dark';document.documentElement.dataset.theme=t;document.documentElement.style.colorScheme=t}catch(e){}})()";

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en" data-theme="dark" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeScript }} />
      </head>
      <body>
        <Providers>
          <SiteHeader />
          <main className="mx-auto min-h-[70vh] max-w-[1600px]">{children}</main>
          <SiteFooter />
        </Providers>
      </body>
    </html>
  );
}
