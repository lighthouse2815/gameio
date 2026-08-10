import type { Metadata } from "next";
import { GameDetailScreen } from "@/features/games/game-detail-screen";

export async function generateMetadata({
  params,
}: {
  params: Promise<{ slug: string }>;
}): Promise<Metadata> {
  const { slug } = await params;
  return { title: slug.replaceAll("-", " ") };
}

export default async function GameDetailPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{ room?: string }>;
}) {
  const { slug } = await params;
  const { room } = await searchParams;
  return <GameDetailScreen slug={slug} roomId={room} />;
}
