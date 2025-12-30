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

  // 1. Subscription Management (Native Events)
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
    const { isReady, setup } = useConnector.getState();

    // 1. Perform auto-setup for dev/test mode if mnemonic is provided in env
    if (!isReady && extra.MNEMONIC_LOCAL && extra.ENCRYPTION_SALT) {
      console.log("Found test mnemonic in env, performing auto-setup.");
      await setup(extra.MNEMONIC_LOCAL, extra.ENCRYPTION_SALT);
    }

    // 2. Start the foreground service
    try {
      await ConnectorModule.start();
      console.log("ConnectorModule started.");
    } catch (e) {
      console.error("Failed to start ConnectorModule:", e);
    }
  }, [extra.ENCRYPTION_SALT, extra.MNEMONIC_LOCAL]);

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
            animation: "fade",
          }}
        />
        <Stack.Screen
          name="setup"
          options={{
            title: "Setup Mnemonic",
            animation: "fade",
          }}
        />
      </Stack>
    </View>
  );
}