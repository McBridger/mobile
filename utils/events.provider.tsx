import NativeBleConnector, { IS_CONNECTED_QUERY } from "@/specs/NativeBleConnector";
import { useQuery, useQueryClient } from "@tanstack/react-query";

type Props = {
  children: React.ReactNode;
};

export const EventsProvider = ({ children }: Props) => {
  const queryClient = useQueryClient();

  useQuery({
    queryKey: ["ble", "events"],
    queryFn: ({ signal }) => {
      return new Promise(() => {
        // Вечный промис
        console.log("[BLE Events] Listener started.");

        const onConnectionUpdate = (isConnected: boolean) => {
          console.log("[BLE Events] Connection state changed:", isConnected);
          queryClient.setQueryData(IS_CONNECTED_QUERY.queryKey, isConnected);
        };

        const subs = [
          NativeBleConnector.onConnected(() => onConnectionUpdate(true)),
          NativeBleConnector.onDisconnected(() => onConnectionUpdate(false)),
        ];

        // RQ вызовет это, когда последний подписчик размонтируется
        signal.onabort = () => {
          console.log("[BLE Events] Listener stopped. Cleaning up.");
          subs.forEach((sub) => sub.remove());
          // Важно: можно также принудительно дисконнектиться при закрытии приложения
          // BleConnector.disconnect();
        };
      });
    },
    staleTime: Infinity,
    refetchOnMount: false,
    refetchOnWindowFocus: false,
  });

  return <>{children}</>;
};
