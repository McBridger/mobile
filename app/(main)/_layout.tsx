import { PATHS } from "@/constants";
import { useAppConfig } from "@/hooks/useConfig";
import ConnectorModule, { useConnector } from "@/modules/connector";
import {
  Redirect,
  Stack,
  useLocalSearchParams,
} from "expo-router";
import { useEffect } from "react";
import { AppState, AppStateStatus, View } from "react-native";
import { useShallow } from "zustand/shallow";
import Header from "../../components/Header";

export default function MainLayout() {
  const { extra } = useAppConfig();
  const params = useLocalSearchParams();
  const [subscribe, unsubscribe] = useConnector(
    useShallow((state) => [state.subscribe, state.unsubscribe])
  );

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

    useEffect(() => {
      ConnectorModule.start(extra.SERVICE_UUID, extra.CHARACTERISTIC_UUID);
      console.log("ConnectorModule started.");
    }, [extra.SERVICE_UUID, extra.CHARACTERISTIC_UUID]);

  if (params.address) {
    return (
      <Redirect
        href={{ pathname: PATHS.CONNECTION, params: { address: params.address } }}
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
