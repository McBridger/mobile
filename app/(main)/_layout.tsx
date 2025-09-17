import { BleConnector } from "@/specs/NativeBleConnector";
import BleScanner from '@/specs/NativeBleScanner';
import { Redirect, Stack, useLocalSearchParams } from "expo-router";
import { useCallback, useEffect } from "react";
import { AppState, AppStateStatus, EventSubscription } from "react-native";
import {
  handleConnected,
  handleConnectionFailed,
  handleDisconnected,
  handleReceived,
} from "../../store/connection.store";
import { handleDeviceFound, handleScanFailed, handleScanStopped } from '../../store/scanner.store';

export default function MainLayout() {
  const params = useLocalSearchParams();

  const subscribe = useCallback(() => {
    return [
      /* Connector Events */
      BleConnector.onConnected(handleConnected),
      BleConnector.onDisconnected(handleDisconnected),
      BleConnector.onConnectionFailed(handleConnectionFailed),
      BleConnector.onReceived(handleReceived),
      /* Scanner Events */
      BleScanner.onDeviceFound(handleDeviceFound),
      BleScanner.onScanFailed(handleScanFailed),
      BleScanner.onScanStopped(handleScanStopped),
    ];
  }, []);

  const unsubscribe = useCallback((subscriptions: EventSubscription[]) => {
    subscriptions.forEach((unsub) => unsub.remove());
  }, []);

  useEffect(() => {
    let subscriptions: EventSubscription[] = [];
    if (AppState.currentState === "active") subscriptions = subscribe();

    const appStateSubscription = AppState.addEventListener(
      "change",
      (nextAppState: AppStateStatus) => {
        if (nextAppState === "active") subscriptions = subscribe();
        else unsubscribe(subscriptions);
      }
    );

    return () => {
      appStateSubscription.remove();
      unsubscribe(subscriptions);
    };
  }, [subscribe, unsubscribe]);

  if (params.address) {
    return (
      <Redirect
        href={{ pathname: "/connection", params: { address: params.address } }}
      />
    );
  }

  return (
    <Stack>
      <Stack.Screen name="devices" options={{ headerShown: false }} />
      <Stack.Screen name="connection" options={{ title: "Connection" }} />
    </Stack>
  );
}
