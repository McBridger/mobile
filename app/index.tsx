import ConnectorModule, { useConnector } from "@/modules/connector";
import { Redirect } from "expo-router";
import { StyleSheet, Text, View } from "react-native";
import { useBluetoothPermissions } from "../hooks/useBluetoothPermissions";
import { useAppConfig } from "@/hooks/useConfig";
import { useEffect } from "react";

// AppRegistry.registerHeadlessTask(
//   BridgerHeadlessTask.name,
//   () => BridgerHeadlessTask
// );

export default function AppEntry() {
  const { extra } = useAppConfig();
  const { isLoading, allPermissionsGranted } = useBluetoothPermissions();
  const isConnected = useConnector((state) => state.status === "connected");

  useEffect(() => {
    ConnectorModule.start(extra.SERVICE_UUID, extra.CHARACTERISTIC_UUID);
    console.log("ConnectorModule started.");
  }, [extra.SERVICE_UUID, extra.CHARACTERISTIC_UUID]);

  if (isLoading) {
    return (
      <View style={styles.container}>
        <Text>Loading app and checking permissions...</Text>
      </View>
    );
  }

  if (!allPermissionsGranted) return <Redirect href="/permissions" />;
  if (isConnected) return <Redirect href="/connection" />;

  return <Redirect href="/devices" />;
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    backgroundColor: "#fff",
  },
});
