import type { Metadata } from "next";
import { HomeScreen } from "@/features/home/home-screen";

export const metadata: Metadata = { title: "Play Network" };

export default function HomePage() {
  return <HomeScreen />;
}
