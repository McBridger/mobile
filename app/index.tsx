import { PATHS, Status } from "@/constants";
import { useConnector } from "@/modules/connector";
import { Redirect } from "expo-router";
import { StyleSheet, Text, View } from "react-native";
import { useBluetoothPermissions } from "../hooks/useBluetoothPermissions";

export default function AppEntry() {
  const { isLoading, allPermissionsGranted } = useBluetoothPermissions();
  const isConnected = useConnector((state) => state.status === Status.Connected);

  if (isLoading) {
    return (
      <View style={styles.container}>
        <Text>Loading app and checking permissions...</Text>
      </View>
    );
  }

  if (!allPermissionsGranted) return <Redirect href={PATHS.PERMISSIONS} />;
  if (isConnected) return <Redirect href={PATHS.CONNECTION} />;

  return <Redirect href={PATHS.DEVICES} />;
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    backgroundColor: "#fff",
  },
});
