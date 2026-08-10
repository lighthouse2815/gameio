import type { Metadata } from "next";
import { ProfileScreen } from "@/features/profile/profile-screen";

export async function generateMetadata({
  params,
}: {
  params: Promise<{ username: string }>;
}): Promise<Metadata> {
  const { username } = await params;
  return { title: username };
}

export default async function ProfilePage({
  params,
}: {
  params: Promise<{ username: string }>;
}) {
  const { username } = await params;
  return <ProfileScreen username={decodeURIComponent(username)} />;
}
