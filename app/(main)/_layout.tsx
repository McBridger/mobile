import { useAppConfig } from "@/hooks/useConfig";
import { BleConnector } from "@/specs/NativeBleConnector";
import BleScanner from "@/specs/NativeBleScanner";
import { Redirect, Stack, useLocalSearchParams } from "expo-router";
import { useCallback, useEffect } from "react";
import { AppState, AppStateStatus, EventSubscription, View } from "react-native";
import Header from "../../components/Header";
import {
  handleConnected,
  handleConnectionFailed,
  handleDisconnected,
  handleReceived,
} from "../../store/connection.store";
import {
  handleDeviceFound,
  handleScanFailed,
  handleScanStopped,
} from "../../store/scanner.store";

export default function MainLayout() {
  const params = useLocalSearchParams();
  const { extra } = useAppConfig();

  const subscribe = useCallback(() => {
    return [
      /* Connector Events */
      BleConnector.onConnected(handleConnected),
      BleConnector.onDisconnected(handleDisconnected),
      BleConnector.onConnectionFailed(handleConnectionFailed),
      BleConnector.onReceived(handleReceived),
      /* Scanner Events */
      BleScanner.onDeviceFound(handleDeviceFound.bind(null, extra)),
      BleScanner.onScanFailed(handleScanFailed),
      BleScanner.onScanStopped(handleScanStopped),
    ];
  }, [extra]);

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
    <View style={{ flex: 1 }}>
      <Header />
      <Stack screenOptions={{ headerShown: false }}>
        <Stack.Screen
          name="devices"
          options={{
            title: "Devices",
            animation: "slide_from_left",
          }}
        />
        <Stack.Screen
          name="connection"
          options={{
            title: "Connection",
            animation: "slide_from_right",
          }}
        />
      </Stack>
    </View>
  );
}
