import ConnectorModule, { useConnector } from "@/modules/connector";
import { Redirect, Stack, useLocalSearchParams } from "expo-router";
import { use, useCallback, useEffect, useState } from "react";
import { AppState, AppStateStatus, View } from "react-native";
import { useShallow } from "zustand/shallow";
import Header from "../../components/Header";
import { useAppConfig } from "@/hooks/useConfig";

export default function MainLayout() {
  const { extra } = useAppConfig();
  const params = useLocalSearchParams();
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
    await ConnectorModule.start();
    console.log("ConnectorModule started.");

    const [advertiseUUID, serviceUUID, characteristicUUID] = await Promise.all([
      ConnectorModule.getAdvertiseUUID(),
      ConnectorModule.getServiceUUID(),
      ConnectorModule.getCharacteristicUUID(),
    ]);
    console.log(
      "Advertise UUID:",
      advertiseUUID,
      "Service UUID:",
      serviceUUID,
      "Characteristic UUID:",
      characteristicUUID
    );

    extra.SERVICE_UUID = serviceUUID;
    extra.CHARACTERISTIC_UUID = characteristicUUID;
    extra.ADVERTISE_UUID = advertiseUUID;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    initialize().then(() => {
      console.log("ConnectorModule started.")
      setInit(true);
    });
  }, [initialize]);

  if (!init) {
    return <View style={{ flex: 1 }} />;
  }

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
