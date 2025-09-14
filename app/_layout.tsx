import { EventsProvider } from "@/utils/events.provider";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Stack } from "expo-router";

const queryClient = new QueryClient();

export default function RootLayout() {
  return (
    <QueryClientProvider client={queryClient}>
      <EventsProvider>
        <Stack />
      </EventsProvider>
    </QueryClientProvider>
  );
}
