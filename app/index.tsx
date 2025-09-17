import { useConnector } from "@/store/connection.store";
import { BridgerHeadlessTask } from "@/utils/headless";
import { Redirect } from "expo-router";
import { AppRegistry, StyleSheet, Text, View } from "react-native";
import { useBluetoothPermissions } from "../hooks/useBluetoothPermissions";

AppRegistry.registerHeadlessTask(
  BridgerHeadlessTask.name,
  () => BridgerHeadlessTask
);

export default function AppEntry() {
  const { isLoading, allPermissionsGranted } = useBluetoothPermissions();
  const isConnected = useConnector((state) => state.status === 'connected');

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
