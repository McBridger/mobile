import { useAppConfig } from "@/hooks/useConfig";
import { BleDevice } from "@/specs/NativeBleScanner";
import { useScanner } from "@/store/scanner.store";
import { useRouter } from "expo-router";
import { useMemo } from "react";
import {
  Button,
  Pressable,
  SectionList,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { useShallow } from "zustand/react/shallow";

type ListDataItem = BleDevice | { id: string; type: "separator" };

export default function Devices() {
  const router = useRouter();
  const { extra } = useAppConfig();
  const isScanning = useScanner((state) => state.isScanning);
  const { devices, bleDevices } = useScanner(
    useShallow((state) => ({
      devices: state.devices,
      bleDevices: state.bleDevices,
    }))
  );
  const [startScan, stopScan, clearDevices] = useScanner(
    useShallow((state) => [state.start, state.stop, state.clear])
  );

  const bleDevicesArray = useMemo(
    () => Array.from(bleDevices.values()),
    [bleDevices]
  );
  const otherDevicesArray = useMemo(
    () => Array.from(devices.values()),
    [devices]
  );

  const sections = useMemo(() => {
    const sectionsData = [];
    if (bleDevicesArray.length > 0) {
      sectionsData.push({ title: "BLE Devices", data: bleDevicesArray });
    }
    if (otherDevicesArray.length > 0) {
      sectionsData.push({ title: "Other Devices", data: otherDevicesArray });
    }
    return sectionsData;
  }, [bleDevicesArray, otherDevicesArray]);

  const handleDevicePress = (device: BleDevice) => {
    if (device.services?.includes(extra.BRIDGER_SERVICE_UUID)) {
      router.push({
        pathname: "/connection",
        params: { address: device.address },
      });
      stopScan();
    }
  };

  const renderItem = ({ item }: { item: ListDataItem }) => {
    if ("type" in item && item.type === "separator") {
      return (
        <View style={styles.separator}>
          <Text style={styles.separatorText}>Other Devices</Text>
        </View>
      );
    }

    const device = item as BleDevice;
    return (
      <Pressable
        onPress={() => handleDevicePress(device)}
        style={({ pressed }) => [
          styles.deviceItem,
          pressed && styles.deviceItemPressed,
        ]}
      >
        <Text style={styles.deviceName}>{device.name || "N/A"}</Text>
        <Text style={styles.deviceInfo}>Address: {device.address}</Text>
        <Text style={styles.deviceInfo}>RSSI: {device.rssi}</Text>
        {device.services?.includes(extra.BRIDGER_SERVICE_UUID) && (
          <Text style={styles.bridgerInfo}>[Bridger Service Found]</Text>
        )}
      </Pressable>
    );
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>BLE Scanner</Text>
      <View style={styles.buttonContainer}>
        <Button
          title="Start Scan"
          onPress={startScan}
          disabled={isScanning}
        />
        <Button title="Stop Scan" onPress={stopScan} disabled={!isScanning} />
        <Button title="Clear Devices" onPress={clearDevices} />
      </View>

      {isScanning && <Text>Scanning for devices...</Text>}

      <SectionList
        style={styles.list}
        sections={sections}
        keyExtractor={(item) => item.address}
        renderItem={renderItem}
        renderSectionHeader={({ section: { title } }) => (
          <Text style={styles.sectionHeader}>{title}</Text>
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
  deviceItemPressed: {
    opacity: 0.7,
  },
  separator: {
    height: 1,
    backgroundColor: "#ccc",
    marginVertical: 10,
    width: "100%",
    alignItems: "center",
  },
  separatorText: {
    backgroundColor: "#f0f0f0",
    paddingHorizontal: 10,
    color: "#888",
    fontSize: 16,
    fontWeight: "bold",
  },
  sectionHeader: {
    fontSize: 18,
    fontWeight: "bold",
    backgroundColor: "#f0f0f0",
    paddingHorizontal: 10,
    paddingVertical: 5,
    marginTop: 10,
  },
});
