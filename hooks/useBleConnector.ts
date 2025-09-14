import BleConnector from "@/specs/NativeBleConnector";
import { useMutation } from "@tanstack/react-query";
import { useCallback, useEffect, useState } from "react";
import { useAppConfig } from "./useConfig";

type Props = {
  onReceived?: (data: string) => void;
};

export function useBleConnector({ onReceived }: Props = {}) {
  const [isConnected, setIsConnected] = useState(false);
  const { extra } = useAppConfig();

  // --- Event Handlers ---
  // These callbacks are the single source of truth for state changes from native events.

  const handleConnected = useCallback(() => {
    console.log("[BLE Hook] Connected.");
    setIsConnected(true);
  }, []);

  const handleDisconnected = useCallback(() => {
    console.log("[BLE Hook] Disconnected.");
    setIsConnected(false);
  }, []);

  const send = useCallback((data: string) => {
    BleConnector.send(data).catch((error) => {
      console.error("[BLE Hook] send() promise rejected.", error);
    });
  }, []);

  const handleReceived = useCallback(
    (data: string) => {
      console.log("[BLE Hook] Received:", data);
      if (onReceived) onReceived(data);
    },
    [onReceived]
  );

  const connect = useCallback((address: string) => {
    BleConnector.connect(address).catch((error) => {
      console.error("[BLE Hook] connect() promise rejected.", error);
    });
  }, []);

  const disconnect = useCallback(() => {
    return BleConnector.disconnect().catch((error) => {
      console.error("[BLE Hook] disconnect() promise rejected.", error);
    });
  }, []);

  const handleInitial = useCallback(async () => {
    const isConnected = await BleConnector.isConnected();
    setIsConnected(isConnected);
    if (isConnected) return;

    const {
      BRIDGER_SERVICE_UUID,
      WRITE_CHARACTERISTIC_UUID,
      NOTIFY_CHARACTERISTIC_UUID,
    } = extra;

    await BleConnector.setup(
      BRIDGER_SERVICE_UUID,
      WRITE_CHARACTERISTIC_UUID,
      NOTIFY_CHARACTERISTIC_UUID
    );
  }, [extra]);

  // --- Effect for Subscriptions ---
  // Sets up and tears down the native event listeners.

  useEffect(() => {
    handleInitial().catch((error) => {
      console.error("[BLE Hook] handleInitial() promise rejected.", error);
    });
  }, [handleInitial]);

  useEffect(() => {
    const subscriptions = [
      // BleConnector.onConnected(handleConnected),
      // BleConnector.onDisconnected(handleDisconnected),
      BleConnector.onReceived(handleReceived),
    ];

    return () => {
      subscriptions.forEach((sub) => sub.remove());
    };
  }, [handleConnected, handleDisconnected, handleReceived]);

  return {
    send,
    isConnected,
    connect,
    disconnect,
  };
}

type ConnectMutationProps = {
  onSuccess?: () => void;
}
export const useConnectMutation = ({onSuccess}: ConnectMutationProps = {}) => {
  const { extra } = useAppConfig();

  return useMutation({
    mutationFn: async (address: string | null) => {
      if (!address) {
        return new Promise<void>(async (resolve, reject) => {
          // 1. Сначала устанавливаем слушателя
          const sub = BleConnector.onDisconnected(() => {
            // Когда событие произойдет, отписываемся и резолвим промис
            sub.remove();
            resolve();
          });

          try {
            // 2. Затем инициируем отключение
            await BleConnector.disconnect();
            // Если disconnect() завершился, но событие по какой-то причине
            // не пришло (например, уже были отключены), можно добавить таймаут
            // или другую логику для избежания вечного ожидания.
          } catch (error) {
            // Если при отключении произошла ошибка, нужно отписаться и реджектнуть промис
            sub.remove();
            reject(error);
          }
        });

        // await BleConnector.disconnect();

        // return new Promise<void>((resolve) => {
        //   return BleConnector.onDisconnected(resolve)
        // })

        // let sub: EventSubscription;

        // return new Promise<void>((resolve) => {
        //   sub = BleConnector.onDisconnected(resolve);
        // }).then(() => sub.remove())

        // return new Promise<void>((resolve) => {
        //   const sub = BleConnector.onDisconnected(() => {
        //     sub.remove();
        //     resolve();
        //   })
        // })
      }

      const {
        BRIDGER_SERVICE_UUID,
        WRITE_CHARACTERISTIC_UUID,
        NOTIFY_CHARACTERISTIC_UUID,
      } = extra;

      await BleConnector.setup(
        BRIDGER_SERVICE_UUID,
        WRITE_CHARACTERISTIC_UUID,
        NOTIFY_CHARACTERISTIC_UUID
      );
      // 2. Затем подключение
      await BleConnector.connect(address);
    },
    onSuccess: (value, address) => {
      console.log(
        `[useConnectMutation] ${address ? "Connected" : "Disconnected"}.`
      );
      if (onSuccess) onSuccess();
    },
    onError: (error) => {
      console.log(`[useConnectMutation] Error: ${error}`);
    },
  });
};