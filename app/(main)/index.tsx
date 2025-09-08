import { useBleScanner } from "@/hooks/useBleScanner";
import { useAppConfig } from "@/hooks/useConfig";
import { Button, FlatList, StyleSheet, Text, View } from "react-native";

export default function Index() {
  const { extra } = useAppConfig();
  const {
    isScanning,
    foundDevices,
    scanError,
    startScan,
    stopScan,
    clearDevices,
  } = useBleScanner();

  return (
    <View style={styles.container}>
      <Text style={styles.title}>BLE Scanner</Text>
      <View style={styles.buttonContainer}>
        <Button title="Start Scan" onPress={startScan} disabled={isScanning} />
        <Button title="Stop Scan" onPress={stopScan} disabled={!isScanning} />
        <Button title="Clear Devices" onPress={clearDevices} />
      </View>

      {isScanning && <Text>Scanning for devices...</Text>}
      {scanError && (
        <Text style={styles.errorText}>
          Scan Error: {scanError.message} (Code: {scanError.code})
        </Text>
      )}

      <Text style={styles.subtitle}>
        Found Devices ({foundDevices.length}):
      </Text>
      <FlatList
        style={styles.list}
        data={foundDevices}
        keyExtractor={(item) => item.address}
        renderItem={({ item }) => (
          <View style={styles.deviceItem}>
            <Text style={styles.deviceName}>{item.name || "N/A"}</Text>
            <Text style={styles.deviceInfo}>Address: {item.address}</Text>
            <Text style={styles.deviceInfo}>RSSI: {item.rssi}</Text>
            {item.services?.includes(extra.BRIDGER_SERVICE_UUID) && (
              <Text style={styles.bridgerInfo}>[Bridger Service Found]</Text>
            )}
          </View>
        )}
        ListEmptyComponent={<Text>No devices found yet.</Text>}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    padding: 20,
    backgroundColor: "#f0f0f0",
  },
  title: {
    fontSize: 24,
    fontWeight: "bold",
    marginBottom: 20,
    color: "#333",
  },
  subtitle: {
    fontSize: 18,
    fontWeight: "600",
    marginTop: 20,
    marginBottom: 10,
    color: "#555",
  },
  buttonContainer: {
    flexDirection: "row",
    justifyContent: "space-around",
    width: "100%",
    marginBottom: 20,
  },
  errorText: {
    color: "red",
    marginTop: 10,
    textAlign: "center",
  },
  list: {
    width: "100%",
    marginTop: 10,
  },
  deviceItem: {
    backgroundColor: "#fff",
    padding: 15,
    borderRadius: 8,
    marginBottom: 10,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.2,
    shadowRadius: 1.41,
    elevation: 2,
  },
  deviceName: {
    fontSize: 16,
    fontWeight: "bold",
    color: "#333",
  },
  deviceInfo: {
    fontSize: 14,
    color: "#666",
  },
  bridgerInfo: {
    fontSize: 14,
    color: "green",
    marginTop: 5,
    fontWeight: "bold",
  },
  permissionItem: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    width: "100%",
    marginBottom: 10,
    paddingHorizontal: 10,
  },
});
