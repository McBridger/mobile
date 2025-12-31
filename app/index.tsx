import { useConnector } from "@/modules/connector";
import { Redirect } from "expo-router";
import { StyleSheet, Text, View } from "react-native";
import { useBluetoothPermissions } from "../hooks/useBluetoothPermissions";

export default function AppEntry() {
  const { isLoading, allPermissionsGranted } = useBluetoothPermissions();
  const isReady = useConnector((state) => state.isReady);

  if (isLoading) {
    return (
      <View style={styles.container}>
        <Text>Loading app and checking permissions...</Text>
      </View>
    );
  }

  // 1. Check Permissions
  if (!allPermissionsGranted) return <Redirect href="/permissions" />;

  // 2. Check Encryption Setup via Store
  if (!isReady) return <Redirect href="/setup" />;

  // 3. If ready, go straight to the Magic Sync screen
  return <Redirect href="/connection" />;
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
  },
});
