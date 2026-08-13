import { HomeScreen } from "@/features/home/home-screen";
import { localizedMetadata } from "@/lib/i18n/server";

export const generateMetadata = () => localizedMetadata("Play Network");

export default function HomePage() {
  return <HomeScreen />;
}
