import { QueryClient } from "@tanstack/react-query";

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        refetchOnWindowFocus: false,
        retry: (count, error) => {
          if (
            typeof error === "object" &&
            error !== null &&
            "status" in error &&
            Number(error.status) < 500
          ) {
            return false;
          }
          return count < 2;
        },
        staleTime: 30_000,
      },
      mutations: {
        retry: false,
      },
    },
  });
}
