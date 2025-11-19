import { PATHS, Status } from "@/constants";
import { useAppConfig } from "@/hooks/useConfig";
import { useConnector } from "@/modules/connector";
import { useFocusEffect, useLocalSearchParams, useRouter } from "expo-router";
import { useCallback, useEffect, useMemo } from "react";
import { useShallow } from "zustand/react/shallow";

export const useConnection = () => {
  const router = useRouter();
  const { extra } = useAppConfig();
  const { address, name } = useLocalSearchParams<{
    address: string;
    name: string;
  }>();

  const {
    status,
    _items,
    connect,
    disconnect,
    send,
    deleteItem,
    addItem,
    clearItems,
  } = useConnector(
    useShallow((state) => ({
      status: state.status,
      _items: state.items,
      connect: state.connect,
      disconnect: state.disconnect,
      send: state.send,
      deleteItem: state.deleteItem,
      addItem: state.addItem,
      clearItems: state.clearItems,
    }))
  );

  const isConnected = useMemo(() => status === Status.Connected, [status]);

  useFocusEffect(
    useCallback(() => {
      if (isConnected || !address) return;
      connect(address, name, extra);
    }, [address, name, connect, isConnected, extra])
  );

  useEffect(() => {
    const unsub = useConnector.subscribe(
      (state) => state.status,
      (newStatus, prevStatus) => {
        if (
          prevStatus === Status.Disconnecting &&
          newStatus === Status.Disconnected
        ) {
          router.push(PATHS.DEVICES);
        }
      }
    );
    return () => unsub();
  }, [router]);

  const items = useMemo(
    () => Array.from(_items.values()).sort((a, b) => b.time - a.time),
    [_items]
  );

  return {
    items,
    isConnected,
    disconnect,
    send,
    deleteItem,
    addItem,
    clearItems,
  };
};
