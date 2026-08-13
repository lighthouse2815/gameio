"use client";

import { useEffect, useRef } from "react";
import type {
  TankSnapshot,
} from "@/features/multiplayer/realtime/types";
import { useI18n } from "@/lib/i18n/use-i18n";

export function TankCanvas({
  snapshot,
  userId,
}: {
  snapshot: TankSnapshot;
  userId?: string;
}) {
  const { t } = useI18n();
  const hostRef = useRef<HTMLDivElement>(null);
  const renderRef = useRef<(next: TankSnapshot, currentUser?: string) => void>(
    () => {},
  );
  const initialSnapshot = useRef(snapshot);
  const initialUserId = useRef(userId);

  useEffect(() => {
    if (!hostRef.current) return;
    const host = hostRef.current;
    let disposed = false;
    let game: import("phaser").Game | null = null;
    let latestSnapshot = initialSnapshot.current;
    let latestUserId = initialUserId.current;

    void import("phaser").then((module) => {
      if (disposed) return;
      const Phaser = module.default;

      class AuthoritativeTankScene extends Phaser.Scene {
        private telemetry!: import("phaser").GameObjects.Graphics;

        constructor() {
          super("authoritative-tank");
        }

        create() {
          this.telemetry = this.add.graphics();
          renderRef.current = (next, currentUser) => {
            latestSnapshot = next;
            latestUserId = currentUser;
            this.drawSnapshot(next, currentUser);
          };
          this.drawSnapshot(latestSnapshot, latestUserId);
        }

        private drawSnapshot(next: TankSnapshot, currentUser?: string) {
          const width = 720;
          const height = 720;
          const scaleX = width / next.width;
          const scaleY = height / next.height;
          this.telemetry.clear();
          this.telemetry.lineStyle(1, 0x2e2e2e, 0.85);
          for (let unit = 0; unit <= 100; unit += 10) {
            this.telemetry.lineBetween(unit * scaleX, 0, unit * scaleX, height);
            this.telemetry.lineBetween(0, unit * scaleY, width, unit * scaleY);
          }
          this.telemetry.lineStyle(3, 0x5c5c5c, 1);
          this.telemetry.strokeRect(1, 1, width - 2, height - 2);

          next.bullets.forEach((bullet) => {
            this.telemetry.fillStyle(0xed1c24, 1);
            this.telemetry.fillCircle(
              bullet.x * scaleX,
              bullet.y * scaleY,
              4,
            );
          });

          next.tanks.forEach((tank) => {
            const x = tank.x * scaleX;
            const y = tank.y * scaleY;
            const own = tank.userId === currentUser;
            const color = !tank.alive
              ? 0x494949
              : own
                ? 0xed1c24
                : 0xe9e5dd;
            this.telemetry.fillStyle(color, tank.alive ? 1 : 0.5);
            this.telemetry.fillCircle(x, y, 14);
            this.telemetry.lineStyle(5, color, tank.alive ? 1 : 0.5);
            const radians = (tank.rotation * Math.PI) / 180;
            this.telemetry.lineBetween(
              x,
              y,
              x + Math.cos(radians) * 24,
              y + Math.sin(radians) * 24,
            );
            this.telemetry.fillStyle(0x292929, 1);
            this.telemetry.fillRect(x - 18, y + 19, 36, 4);
            this.telemetry.fillStyle(
              tank.hp <= 25 ? 0xed1c24 : 0xe9e5dd,
              1,
            );
            this.telemetry.fillRect(
              x - 18,
              y + 19,
              36 * (tank.hp / 100),
              4,
            );
          });
        }
      }

      game = new Phaser.Game({
        type: Phaser.AUTO,
        width: 720,
        height: 720,
        parent: host,
        backgroundColor: "#0d0d0d",
        render: {
          antialias: true,
          pixelArt: false,
        },
        scale: {
          mode: Phaser.Scale.FIT,
          autoCenter: Phaser.Scale.CENTER_BOTH,
        },
        scene: AuthoritativeTankScene,
      });
    });

    return () => {
      disposed = true;
      renderRef.current = () => {};
      game?.destroy(true);
      host.replaceChildren();
    };
  }, []);

  useEffect(() => {
    renderRef.current(snapshot, userId);
  }, [snapshot, userId]);

  return (
    <div
      ref={hostRef}
      className="game-canvas aspect-square w-full bg-[#0d0d0d]"
      aria-label={t("Authoritative Tank Battle renderer")}
    />
  );
}
