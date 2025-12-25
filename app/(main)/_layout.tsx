import { useAppConfig } from "@/hooks/useConfig";
import ConnectorModule, { useConnector } from "@/modules/connector";
import { Stack } from "expo-router";
import { useCallback, useEffect, useState } from "react";
import { AppState, AppStateStatus, View } from "react-native";
import { useShallow } from "zustand/shallow";
import Header from "../../components/Header";

export default function MainLayout() {
  const { extra } = useAppConfig();
  const [subscribe, unsubscribe] = useConnector(
    useShallow((state) => [state.subscribe, state.unsubscribe])
  );
  const [init, setInit] = useState(false);

  useEffect(() => {
    if (AppState.currentState === "active") subscribe();

    const appStateSubscription = AppState.addEventListener(
      "change",
      (nextAppState: AppStateStatus) => {
        if (nextAppState === "active") subscribe();
        else unsubscribe();
      }
    );

    return () => {
      appStateSubscription.remove();
      unsubscribe();
    };
  }, [subscribe, unsubscribe]);

  const initialize = useCallback(async () => {
    // 1. Perform auto-setup for dev/test mode if mnemonic is provided in env
    // IMPORTANT: This must happen BEFORE start() because start() derives UUIDs
    if (!ConnectorModule.isReady() && extra.MNEMONIC_LOCAL && extra.ENCRYPTION_SALT) {
      console.log("Found test mnemonic in env, performing auto-setup.");
      ConnectorModule.setup(extra.MNEMONIC_LOCAL, extra.ENCRYPTION_SALT);
      
      // Trigger discovery automatically in dev mode
      ConnectorModule.startDiscovery();
    }

    // 2. Start the foreground service
    try {
      await ConnectorModule.start();
      console.log("ConnectorModule started.");
    } catch (e) {
      console.error("Failed to start ConnectorModule:", e);
    }

    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    initialize().then(() => setInit(true));
  }, [initialize]);

  if (!init) {
    return <View style={{ flex: 1 }} />;
  }

  return (
    <View style={{ flex: 1 }}>
      <Header />
      <Stack screenOptions={{ headerShown: false }}>
        <Stack.Screen
          name="connection"
          options={{
            title: "Connection",
            animation: "slide_from_right",
          }}
        />
        <Stack.Screen
          name="setup"
          options={{
            title: "Setup Mnemonic",
            animation: "slide_from_bottom",
          }}
        />
      </Stack>
    </View>
  );
}
