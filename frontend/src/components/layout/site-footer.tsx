import Link from "next/link";

const footerLinks = [
  ["Game index", "/games"],
  ["Leaderboard", "/leaderboard"],
  ["Friends", "/friends"],
  ["Settings", "/settings"],
] as const;

export function SiteFooter() {
  return (
    <footer className="mx-auto mt-20 max-w-[1600px] border-x border-t border-[var(--line)] bg-[var(--surface)]">
      <div className="grid lg:grid-cols-[1fr_1fr]">
        <div className="border-b border-[var(--line)] p-6 lg:border-b-0 lg:border-r lg:p-10">
          <p className="text-4xl font-black uppercase tracking-[-0.06em] sm:text-6xl">
            Gameio
          </p>
          <p className="font-telemetry mt-5 text-[9px] leading-5 text-[var(--muted)]">
            INDEPENDENT PLAY NETWORK / WEB BUILD
            <br />
            OPERATIONS NODE: VN-SGN-01
          </p>
        </div>
        <nav
          className="grid grid-cols-2 gap-px bg-[var(--line)]"
          aria-label="Footer navigation"
        >
          {footerLinks.map(([label, href], index) => (
            <Link
              key={href}
              href={href}
              className="font-telemetry flex min-h-20 items-end justify-between bg-[var(--surface)] p-4 text-[9px] hover:text-[var(--accent)] sm:p-5"
            >
              <span>0{index + 1}</span>
              <span>{label}</span>
            </Link>
          ))}
        </nav>
      </div>
      <div className="font-telemetry flex flex-wrap justify-between gap-3 border-t border-[var(--line)] px-5 py-4 text-[8px] text-[var(--muted)]">
        <span>© {new Date().getUTCFullYear()} GAMEIO</span>
        <span>NO GRADIENTS / NO CLIENT AUTHORITY</span>
      </div>
    </footer>
  );
}
