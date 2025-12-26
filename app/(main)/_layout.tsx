import { useAppConfig } from "@/hooks/useConfig";
import ConnectorModule, { useConnector } from "@/modules/connector";
import { Stack, useRouter, useSegments } from "expo-router";
import { useCallback, useEffect, useRef, useState } from "react";
import { AppState, AppStateStatus, View } from "react-native";
import { useShallow } from "zustand/shallow";
import Header from "../../components/Header";

export default function MainLayout() {
  const router = useRouter();
  const segments = useSegments();
  const { extra } = useAppConfig();
  
  const brokerStatus = useConnector((state) => state.brokerStatus);
  const prevStatus = useRef(brokerStatus);

  const [subscribe, unsubscribe] = useConnector(
    useShallow((state) => [state.subscribe, state.unsubscribe])
  );
  
  const [init, setInit] = useState(false);

  // 1. Subscription Management
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

  // 2. Reactive Routing
  useEffect(() => {
    if (!init) return;

    const currentRoute = segments[segments.length - 1];

    // Auto-navigate to setup if broker becomes idle (e.g. after reset)
    if (brokerStatus === "idle" && currentRoute !== "setup") {
      console.log("[MainLayout] Broker is idle, redirecting to setup.");
      router.replace("/setup");
    } 
    
    // Auto-navigate to connection ONLY when finishing the setup process
    // (Transitioning from encrypting to ready/discovering while on setup screen)
    const justFinishedSetup = prevStatus.current === "encrypting" && 
      ["ready", "discovering", "connecting", "connected"].includes(brokerStatus);

    if (justFinishedSetup && currentRoute === "setup") {
      console.log(`[MainLayout] Setup finished (${brokerStatus}), redirecting to connection.`);
      router.replace("/connection");
    }

    prevStatus.current = brokerStatus;
  }, [brokerStatus, init, segments, router]);

  const initialize = useCallback(async () => {
    // 1. Perform auto-setup for dev/test mode if mnemonic is provided in env
    if (!ConnectorModule.isReady() && extra.MNEMONIC_LOCAL && extra.ENCRYPTION_SALT) {
      console.log("Found test mnemonic in env, performing auto-setup.");
      await ConnectorModule.setup(extra.MNEMONIC_LOCAL, extra.ENCRYPTION_SALT);
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
